package redis.storage;

import java.util.List;

sealed interface Value {

    record StringValue(String value) implements Value {
    }

    record ListValue(List<String> value) implements Value {
    }
}
