package redis.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import redis.commands.EffectChannel;
import redis.commands.Scheduler;
import redis.storage.DataPersistency.Eternal;
import redis.storage.DataPersistency.ExpiredAt;
import redis.storage.StorageException.StreamOperationsException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class Storage {

    private final Strings strings = new Strings(this);
    private final Lists lists = new Lists(this);
    private final Streams streams = new Streams(this);

    private final Map<Key, DataPersistency> db = new HashMap<>();
    private final Map<Key, List<Watcher>> watchers = new HashMap<>();

    @NonNull
    static Value extractFrom(@NonNull DataPersistency persistency) {
        return switch (persistency) {
            case Eternal eternal -> eternal.value();
            case ExpiredAt expiredAt -> expiredAt.value();
        };
    }

    private static @Nullable DataPersistency persistency(DataPersistency v) {
        return switch (v) {
            case Eternal eternal -> eternal;
            case ExpiredAt expiredAt -> System.currentTimeMillis() > expiredAt.timestamp() ? null : expiredAt;
        };
    }

    @NullMarked
    static List<Entry> intoEntries(List<StorageEntry> found) {
        return found.stream().map(i -> new Entry(i.id(), i.tuples())).toList();
    }

    @Nullable
    Value lookup(@NonNull Key key) {
        var found = db.get(key);
        if (found == null) {
            return null;
        }
        var live = persistency(found);
        if (live == null) {
            db.remove(key);
            return null;
        }
        return extractFrom(live);
    }

    void mutate(@NonNull Key key, @NonNull BiFunction<Key, DataPersistency, DataPersistency> remapping) {
        var value = db.get(key);
        var v = value == null ? null : persistency(value);
        var result = remapping.apply(key, v);
        if (result == null) {
            db.remove(key);
        } else {
            db.put(key, result);
        }
    }

    void mutateIfPresent(@NonNull Key key, @NonNull BiFunction<Key, DataPersistency, DataPersistency> remapping) {
        var value = db.get(key);
        if (value == null) {
            return;
        }
        var live = persistency(value);
        if (live == null) {
            db.remove(key);
            return;
        }
        var result = remapping.apply(key, live);
        if (result == null) {
            db.remove(key);
        } else {
            db.put(key, result);
        }
    }

    @NullMarked
    <T> @Nullable T readIfPresent(Key key, Function<Value, T> reader) {
        var value = lookup(key);
        if (value == null) {
            return null;
        }
        return reader.apply(value);
    }

    @NonNull
    public DataType type(@NonNull Key key) {
        return DataType.parse(lookup(key));
    }

    @Nullable
    public String get(@NonNull Key key) {
        return strings.get(key);
    }

    public void put(@NonNull Key key, @NonNull String value) {
        noReturnModification(key, () -> strings.put(key, value));
    }

    public void putExpirable(@NonNull Key key, @NonNull String value, @NonNull Duration duration) {
        noReturnModification(key, () -> strings.putExpirable(key, value, duration));
    }

    public int addElement(@NonNull Key key, @NonNull List<String> values) {
        return modification(key, () -> lists.add(key, values));
    }

    public int prependElements(@NonNull Key key, @NonNull List<String> values) {
        return modification(key, () -> lists.prepend(key, values));
    }

    @NonNull
    public List<String> sublist(@NonNull Key key, int start, int stop) {
        return lists.sublist(key, start, stop);
    }

    public int lengthOf(@NonNull Key key) {
        return lists.length(key);
    }

    @Nullable
    public List<String> pop(@NonNull Key key, int howMany) {
        return modification(key, () -> lists.pop(key, howMany));
    }

    public void blockingPop(@NonNull Key key, @NonNull EffectChannel channel, @NonNull Consumer<String> onValue,
                            @NonNull Runnable onTimeout, @NonNull Scheduler scheduler, long timeoutMs) {
        lists.blockingPop(key, channel, onValue, onTimeout, scheduler, timeoutMs);
    }

    @NonNull
    public EntryId add(Key streamKey, Entry entry) throws StreamOperationsException {
        return modification(streamKey, () -> streams.add(streamKey, entry));
    }

    @NullMarked
    public List<Entry> range(Key key, String start, String end) {
        var found = streams.range(key, start, end);
        return intoEntries(found);
    }

    @NullMarked
    public List<Entry> readStreamFrom(Key key, String id) {
        var found = streams.readSince(key, id);
        return intoEntries(found);
    }

    public void blockingReadStreamFrom(@NonNull Key key, @NonNull String id, @NonNull EffectChannel channel,
                                       @NonNull Consumer<List<Entry>> onValue,
                                       @NonNull Runnable onTimeout, @NonNull Scheduler scheduler, long timeoutMs) {
        streams.blockingRead(key, id, channel, onValue, onTimeout, scheduler, timeoutMs);
    }

    public void closeConnection(@NonNull EffectChannel channel) {
        lists.closeConnection(channel);
        streams.closeConnection(channel);
    }

    @NullMarked
    public long increment(Key key) throws StorageException.OperationAllowedOnlyForSingleElement, StorageException.OperationAllowedOnlyForIntegers {
        return modificationLong(key, () -> strings.increment(key));
    }

    public void registerWatcher(Key key, Watcher watcher) {
        watchers.compute(key, (_, v) -> {
            if (v == null) {
                v = new ArrayList<>();
            }
            v.add(watcher);
            return v;
        });
    }

    public void deregisterWatcher(Key key, Watcher watcher) {
        watchers.compute(key, (_, v) -> {
            if (v == null) {
                return null;
            }
            v.remove(watcher);
            if (v.isEmpty()) {
                return null;
            } else {
                return v;
            }
        });
    }

    private <T> T modification(Key key, Supplier<T> value) {
        var result = value.get();
        notifyWatchers(key);
        return result;
    }

    private long modificationLong(Key key, LongSupplier value) {
        var result = value.getAsLong();
        notifyWatchers(key);
        return result;
    }

    private void noReturnModification(Key key, Runnable value) {
        value.run();
        notifyWatchers(key);
    }

    void notifyWatchers(Key key) {
        var keyWatchers = watchers.get(key);
        if (keyWatchers != null) {
            keyWatchers.forEach(watcher -> watcher.onValueChanged(key));
        }
    }
}
