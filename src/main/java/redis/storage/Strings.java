package redis.storage;

import redis.storage.DataPersistency.ExpiredAt;
import redis.storage.DataPersistency.Eternal;
import redis.storage.Value.StringValue;

import redis.storage.StorageException.CannotModifyType;
import redis.storage.StorageException.OperationAllowedOnlyForSingleElement;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * String-shaped operations. Atomicity comes from {@link Storage#mutate};
 * this class never touches the keyspace directly.
 */
final class Strings {

    private final Storage storage;

    Strings(Storage storage) {
        this.storage = storage;
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
}
