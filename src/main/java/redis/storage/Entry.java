package redis.storage;

import org.jspecify.annotations.NonNull;

import java.util.List;

public record Entry(String id, @NonNull List<Tuple> tuples) {
}
