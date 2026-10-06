package redis.storage;

sealed interface DataPersistency {

    record Eternal(Value value) implements DataPersistency {
    }

    record ExpiredAt(Value value, long timestamp) implements DataPersistency {
    }
}
