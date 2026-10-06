package redis.storage;

import redis.storage.Value.ListValue;
import redis.storage.Value.StringValue;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public enum DataType {
    STRING,
    LIST,
    NONE;

    @NonNull
    static DataType parse(@Nullable Value value) {
        if (value == null) {
            return NONE;
        }
        return switch (value) {
            case ListValue _ -> LIST;
            case StringValue _ -> STRING;
        };
    }
}
