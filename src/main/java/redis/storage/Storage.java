package redis.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import redis.storage.DataPersistency.Eternal;
import redis.storage.DataPersistency.ExpiredAt;
import redis.storage.StorageException.StreamOperationsException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class Storage {

    public static final Storage STORAGE = new Storage();

    private final Strings strings = new Strings(this);
    private final Lists lists = new Lists(this);
    private final Streams streams = new Streams(this);

    private final Map<Key, DataPersistency> db = new ConcurrentHashMap<>();
    private final Map<Key, List<Watcher>> watchers = new ConcurrentHashMap<>();

    private Storage() {
    }

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
    private static List<Entry> intoEntries(List<StorageEntry> found) {
        return found.stream().map(i -> new Entry(i.id(), i.tuples())).toList();
    }

    @Nullable
    Value lookup(@NonNull Key key) {
        var found = db.computeIfPresent(key, (_, v) -> persistency(v));
        if (found == null) {
            return null;
        }
        return extractFrom(found);
    }

    void mutate(@NonNull Key key, @NonNull BiFunction<Key, DataPersistency, DataPersistency> remapping) {
        db.compute(key, (aKey, value) -> {
            if (value == null) {
                return remapping.apply(key, null);
            }
            var v = persistency(value);
            return remapping.apply(aKey, v);
        });
    }

    void mutateIfPresent(@NonNull Key key, @NonNull BiFunction<Key, DataPersistency, DataPersistency> remapping) {
        db.computeIfPresent(key, (aKey, value) -> {
            var live = persistency(value);
            return live == null ? null : remapping.apply(aKey, live);
        });
    }

    @NullMarked
    <T> @Nullable T readIfPresent(@NonNull Key key, Function<Value, T> reader) {
        var result = new AtomicReference<T>();
        db.computeIfPresent(key, (aKey, value) -> {
            var live = persistency(value);
            if (live == null) {
                return null;
            }
            result.set(reader.apply(extractFrom(live)));
            return live;
        });
        return result.get();
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

    @NonNull
    public FutureWithTimeoutCleanup<String> blockingPop(@NonNull Key key) {
        return lists.blockingPop(key);
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

    @NullMarked
    public FutureWithTimeoutCleanup<List<Entry>> blockingReadStreamFrom(Key key, String id) {
        return streams.blockingRead(key, id).map(Storage::intoEntries);
    }

    @NullMarked
    public long increment(Key key) throws StorageException.OperationAllowedOnlyForSingleElement, StorageException.OperationAllowedOnlyForIntegers {
        return modificationLong(key, () -> strings.increment(key));
    }

    public void registerWatcher(Key key, Watcher watcher) {
        watchers.compute(key, (k, v) -> v == null ? new ArrayList<>() : v).add(watcher);
    }

    public void deregisterWatcher(Key key, Watcher watcher) {
        watchers.compute(key, (k, v) -> {
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
        watchers.getOrDefault(key, List.of()).forEach(watcher -> watcher.onValueChanged(key));
    }
}
