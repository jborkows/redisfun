import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class Storage {

    static final Storage STORAGE = new Storage();
    private static final Map<String, Object> db = new ConcurrentHashMap<>();

    Object get(String key) {
        return db.get(key);
    }

    void put(String key, Object value) {
        db.put(key, value);
    }
}
