package redis.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import redis.storage.DataPersistency.Eternal;
import redis.storage.DataPersistency.ExpiredAt;
import redis.storage.StorageException.CannotModifyType;
import redis.storage.StorageException.OperationAllowedOnlyForSingleElement;
import redis.storage.Value.StringValue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * String-shaped operations. Atomicity comes from {@link Storage#mutate};
 * this class never touches the keyspace directly.
 */
final class Strings {

    private final Storage storage;

    Strings(Storage storage) {
        this.storage = storage;
    }

    private static DataPersistency copy(DataPersistency original, Value value) {
        return switch (original) {
            case Eternal eternal -> new Eternal(value);
            case ExpiredAt expiredAt -> new ExpiredAt(value, expiredAt.timestamp());
        };
    }

    @Nullable
    String get(@NonNull String key) {
        var value = storage.lookup(key);
        if (value == null) {
            return null;
        }
        return switch (value) {
            case StringValue stringValue -> stringValue.value();
            default -> throw new OperationAllowedOnlyForSingleElement();
        };
    }

    void put(@NonNull String key, @NonNull String value) {
        insert(key, new Eternal(new StringValue(value)));
    }

    void putExpirable(@NonNull String key, @NonNull String value, @NonNull Duration duration) {
        var future = System.currentTimeMillis() + duration.toMillis();
        insert(key, new ExpiredAt(new StringValue(value), future));
    }

    private void insert(@NonNull String key, @NonNull DataPersistency data) {
        storage.mutate(key, (_, v) -> {
            if (v == null) {
                return data;
            }
            return switch (Storage.extractFrom(v)) {
                case StringValue _ -> data;
                default -> throw CannotModifyType.cannotModifyFromList();
            };
        });
    }

    @NullMarked
    public long increment(String key) throws StorageException.OperationAllowedOnlyForSingleElement, StorageException.OperationAllowedOnlyForIntegers {
        AtomicLong atomicLong = new AtomicLong();
        storage.mutate(key, (_, v) -> {
            if (v == null) {
                atomicLong.set(1L);
                return new Eternal(new StringValue("1"));
            } else {
                var original = Storage.extractFrom(v);
                if (original instanceof StringValue(String originalValue)) {
                    try {
                        long value = Long.parseLong(originalValue) + 1;
                        atomicLong.set(value);
                        return copy(v, new StringValue(String.valueOf(value)));
                    } catch (NumberFormatException e) {
                        throw new StorageException.OperationAllowedOnlyForIntegers();
                    }
                } else {
                    throw new StorageException.OperationAllowedOnlyForSingleElement();
                }

            }
        });
        return atomicLong.get();
    }
}
