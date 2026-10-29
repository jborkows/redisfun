package redis;

public sealed interface ServerKind {
    final class Master implements ServerKind {
    }

    record Slave(Location replicaOf) implements ServerKind {
    }

}
