package redis.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import redis.storage.DataPersistency.Eternal;
import redis.storage.DataPersistency.ExpiredAt;
import redis.storage.StorageException.OperationAllowedOnlyForSingleElement;
import redis.storage.Value.StringValue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * String-shaped operations. Atomicity comes from {@link Storage#mutate};
 * this class never touches the keyspace directly.
 */
final class Strings {

    private final Storage storage;

    Strings(Storage storage) {
        this.storage = storage;
    }

    @NullMarked
    private static DataPersistency copy(DataPersistency original, Value value) {
        return switch (original) {
            case Eternal _ -> new Eternal(value);
            case ExpiredAt expiredAt -> new ExpiredAt(value, expiredAt.timestamp());
        };
    }

    @Nullable
    String get(@NonNull Key key) {
        var value = storage.lookup(key);
        if (value == null) {
            return null;
        }
        return switch (value) {
            case StringValue stringValue -> stringValue.value();
            default -> throw new OperationAllowedOnlyForSingleElement();
        };
    }

    void put(@NonNull Key key, @NonNull String value) {
        insert(key, new Eternal(new StringValue(value)));
    }

    void putExpirable(@NonNull Key key, @NonNull String value, @NonNull Duration duration) {
        var future = System.currentTimeMillis() + duration.toMillis();
        insert(key, new ExpiredAt(new StringValue(value), future));
    }

    private void insert(@NonNull Key key, @NonNull DataPersistency data) {
        mutate(key, () -> data, (_, _) -> data);
    }

    @NullMarked
    public long increment(Key key) throws StorageException.OperationAllowedOnlyForSingleElement, StorageException.OperationAllowedOnlyForIntegers {
        var atomicLong = new AtomicLong();
        mutate(key, () -> {
            atomicLong.set(1L);
            return new Eternal(new StringValue("1"));
        }, (d, v) -> {
            try {
                long value = Long.parseLong(v) + 1;
                atomicLong.set(value);
                return copy(d, new StringValue(String.valueOf(value)));
            } catch (NumberFormatException e) {
                throw new StorageException.OperationAllowedOnlyForIntegers();
            }
        });
        return atomicLong.get();
    }

    @NullMarked
    private void mutate(Key key, Supplier<DataPersistency> defaultValue, BiFunction<DataPersistency, String, DataPersistency> mutator) {
        storage.mutate(key, (_, v) -> {
            if (v == null) {
                return defaultValue.get();
            } else {
                var original = Storage.extractFrom(v);
                if (original instanceof StringValue(String originalValue)) {
                    return mutator.apply(v, originalValue);
                } else {
                    throw new StorageException.OperationAllowedOnlyForSingleElement();
                }
            }
        });
    }
}
