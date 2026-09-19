import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

sealed interface DataPersistency {

}

final class Storage {

    static final Storage STORAGE = new Storage();
    private static final Map<String, DataPersistency> db = new ConcurrentHashMap<>();

    Object get(@NonNull String key) {
        var found = db.computeIfPresent(key, (k, v) -> switch (v) {
            case Eternal eternal -> eternal;
            case ExpiredAt expiredAt -> System.currentTimeMillis() > expiredAt.timestamp() ? null : expiredAt;
        });
        return switch (found) {
            case null -> null;
            case Eternal eternal -> eternal.value();
            case ExpiredAt expiredAt -> expiredAt.value();
        };
    }

    void put(@NonNull String key, Object value) {
        db.put(key, new Eternal(value));
    }

    void putExpirable(String key, Object value, @NonNull Duration duration) {
        var future = System.currentTimeMillis() + duration.toMillis();
        db.put(key, new ExpiredAt(value, future));
    }
}

record Eternal(Object value) implements DataPersistency {
}

record ExpiredAt(Object value, long timestamp) implements DataPersistency {
}
