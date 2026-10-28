package redis.storage;

import java.util.List;

public sealed interface Value {

    record StringValue(String value) implements Value {
    }

    record ListValue(List<String> value) implements Value {
    }

    record StreamValue(List<StorageEntry> value) implements Value {
    }
}
