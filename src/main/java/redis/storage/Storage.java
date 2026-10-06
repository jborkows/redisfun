package redis.storage;

import redis.storage.DataPersistency.ExpiredAt;
import redis.storage.DataPersistency.Eternal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public final class Storage {

    public static final Storage STORAGE = new Storage();

    private final Strings strings = new Strings(this);
    private final Lists lists = new Lists(this);

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

    @Nullable
    Value lookup(@NonNull String key) {
        var found = db.computeIfPresent(key, (_, v) -> switch (v) {
            case Eternal eternal -> eternal;
            case ExpiredAt expiredAt -> System.currentTimeMillis() > expiredAt.timestamp() ? null : expiredAt;
        });
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
        db.compute(key, remapping);
    }

    /**
     * Like {@link #mutate}, but only when the key is currently present.
     */
    void mutateIfPresent(@NonNull String key, @NonNull BiFunction<String, DataPersistency, DataPersistency> remapping) {
        db.computeIfPresent(key, remapping);
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
}
