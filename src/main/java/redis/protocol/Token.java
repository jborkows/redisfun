package redis.protocol;

public sealed interface Token {
}

final class Star implements Token {
}

record Length(int value) implements Token {
}

final class Dollar implements Token {
}

record Text(String value) implements Token {
}

record InvalidToken(byte[] bytes) implements Token {
}
