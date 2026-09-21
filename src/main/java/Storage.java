import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

sealed interface DataPersistency {

}

sealed interface Value {

}

final class Storage {

    static final Storage STORAGE = new Storage();
    private static final Map<String, DataPersistency> db = new ConcurrentHashMap<>();

    private static Value extractFrom(DataPersistency persistency) {
        return switch (persistency) {
            case Eternal eternal -> eternal.value();
            case ExpiredAt expiredAt -> expiredAt.value();
        };
    }

    String get(@NonNull String key) throws StorageException {
        var found = db.computeIfPresent(key, (k, v) -> switch (v) {
            case Eternal eternal -> eternal;
            case ExpiredAt expiredAt -> System.currentTimeMillis() > expiredAt.timestamp() ? null : expiredAt;
        });
        if (found == null) {
            return null;
        }

        var value = switch (found) {
            case Eternal eternal -> eternal.value();
            case ExpiredAt expiredAt -> expiredAt.value();
        };
        return switch (value) {
            case ListValue _ -> throw new OperationAllowedOnlyForSingleElement();
            case StringValue stringValue -> stringValue.value();
        };
    }

    void put(@NonNull String key, String value) throws StorageException {
        insert(key, new Eternal(new StringValue(value)));
    }

    void putExpirable(String key, String value, @NonNull Duration duration) throws StorageException {
        var future = System.currentTimeMillis() + duration.toMillis();
        insert(key, new ExpiredAt(new StringValue(value), future));
    }

    private void insert(@NonNull String key, @NonNull DataPersistency data) throws StorageException {
        db.compute(key, (k, v) -> {
            if (v == null) {
                return data;
            }
            var value = extractFrom(v);
            return switch (value) {
                case ListValue _ -> throw CannotModifyType.cannotModifyFromList();
                case StringValue _ -> data;
            };
        });
    }

    int addElement(@NonNull String key, @NonNull List<String> values) {
        var wrapper = new AtomicInteger(0);
        db.compute(key, (k, v) -> {
            if (v == null) {
                wrapper.set(values.size());
                return new Eternal(new ListValue(new ArrayList<>(values)));
            }
            return switch (extractFrom(v)) {
                case ListValue listValue -> {
                    listValue.value().addAll(values);
                    wrapper.set(listValue.value().size());
                    yield v;
                }
                case StringValue _ -> throw CannotModifyType.cannotModifyFromElement();
            };
        });
        return wrapper.get();
    }

    int count(String key) throws OperationAllowedOnlyForLists {
        var data = db.get(key);
        if (data == null) {
            return 0;
        }
        return switch (extractFrom(data)) {
            case ListValue listValue -> listValue.value().size();
            case StringValue _ -> throw new OperationAllowedOnlyForLists();
        };

    }

}

sealed abstract class StorageException extends RuntimeException {

}

final class OperationAllowedOnlyForSingleElement extends StorageException {

}

final class OperationAllowedOnlyForLists extends StorageException {

}

final class CannotModifyType extends StorageException {
    final String existingType;

    CannotModifyType(String existingType) {
        this.existingType = existingType;
    }

    static CannotModifyType cannotModifyFromElement() {
        return new CannotModifyType("Element");
    }

    static CannotModifyType cannotModifyFromList() {
        return new CannotModifyType("List");
    }

}

record StringValue(String value) implements Value {
}

record ListValue(List<String> value) implements Value {
}

record Eternal(Value value) implements DataPersistency {
}

record ExpiredAt(Value value, long timestamp) implements DataPersistency {
}
