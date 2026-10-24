package redis.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import redis.storage.DataPersistency.Eternal;
import redis.storage.DataPersistency.ExpiredAt;
import redis.storage.StorageException.StreamOperationsException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class Storage {

    public static final Storage STORAGE = new Storage();

    private final Strings strings = new Strings(this);
    private final Lists lists = new Lists(this);
    private final Streams streams = new Streams(this);

    private final Map<String, DataPersistency> db = new ConcurrentHashMap<>();

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
    Value lookup(@NonNull String key) {
        var found = db.computeIfPresent(key, (_, v) -> persistency(v));
        if (found == null) {
            return null;
        }
        return extractFrom(found);
    }

    /**
     * Atomically replaces the entry under {@code key} (whether absent or present) using
     * {@code remapping}. This is the unit of atomicity for every mutating operation.
     */
    void mutate(@NonNull String key, @NonNull BiFunction<String, DataPersistency, DataPersistency> remapping) {
        db.compute(key, (aKey, value) -> {
            if (value == null) {
                return remapping.apply(key, null);
            }
            var v = persistency(value);
            return remapping.apply(aKey, v);
        });
    }

    /**
     * Like {@link #mutate}, but {@code remapping} runs only when a live (non-expired) entry
     * is present. Expired entries are treated as absent: they are removed without invoking
     * {@code remapping}, so the lambda never receives {@code null}.
     */
    void mutateIfPresent(@NonNull String key, @NonNull BiFunction<String, DataPersistency, DataPersistency> remapping) {
        db.computeIfPresent(key, (aKey, value) -> {
            var live = persistency(value);
            return live == null ? null : remapping.apply(aKey, live);
        });
    }

    /**
     * Runs {@code reader} while holding the lock for {@code key}, but only when a live
     * (non-expired) entry is present; expired entries are removed and treated as absent.
     * Intended for read-only snapshots that must stay consistent with concurrent mutations.
     *
     * @return the reader's result, or {@code null} when the key is absent or expired
     */
    @NullMarked
    <T> @Nullable T readIfPresent(String key, Function<Value, T> reader) {
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
    public DataType type(@NonNull String key) {
        return DataType.parse(lookup(key));
    }

    @Nullable
    public String get(@NonNull String key) {
        return strings.get(key);
    }

    public void put(@NonNull String key, @NonNull String value) {
        strings.put(key, value);
    }

    public void putExpirable(@NonNull String key, @NonNull String value, @NonNull Duration duration) {
        strings.putExpirable(key, value, duration);
    }

    public int addElement(@NonNull String key, @NonNull List<String> values) {
        return lists.add(key, values);
    }

    public int prependElements(@NonNull String key, @NonNull List<String> values) {
        return lists.prepend(key, values);
    }

    @NonNull
    public List<String> sublist(@NonNull String key, int start, int stop) {
        return lists.sublist(key, start, stop);
    }

    public int lengthOf(@NonNull String key) {
        return lists.length(key);
    }

    @Nullable
    public List<String> pop(@NonNull String key, int howMany) {
        return lists.pop(key, howMany);
    }

    @NonNull
    public FutureWithTimeoutCleanup<String> blockingPop(@NonNull String key) {
        return lists.blockingPop(key);
    }

    /**
     *
     * @param streamKey name of stream
     * @param entry     entry added to stream identified with streamKey
     * @return entryId
     */
    @NonNull
    public String add(String streamKey, Entry entry) throws StreamOperationsException {
        return streams.add(streamKey, entry);
    }

    @NullMarked
    public List<Entry> range(String key, String start, String end) {
        var found = streams.range(key, start, end);
        return intoEntries(found);
    }

    @NullMarked
    public List<Entry> readStreamFrom(String key, String id) {
        var found = streams.readSince(key, id);
        return intoEntries(found);
    }

    @NullMarked
    public FutureWithTimeoutCleanup<List<Entry>> blockingReadStreamFrom(String key, String id) {
        return streams.blockingRead(key, id).map(Storage::intoEntries);
    }

    @NullMarked
    public long increment(String key) throws StorageException.OperationAllowedOnlyForSingleElement, StorageException.OperationAllowedOnlyForIntegers {
        return strings.increment(key);
    }

}
