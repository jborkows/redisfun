package redis.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import redis.storage.Value.ListValue;
import redis.storage.Value.StringValue;

public enum DataType {
    STRING,
    LIST,
    STREAM,
    NONE;

    @NonNull
    static DataType parse(@Nullable Value value) {
        if (value == null) {
            return NONE;
        }
        return switch (value) {
            case ListValue _ -> LIST;
            case StringValue _ -> STRING;
            case Value.StreamValue _ -> STREAM;
        };
    }
}
