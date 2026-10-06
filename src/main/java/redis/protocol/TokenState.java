package redis.protocol;

public sealed interface TokenState {
    NotReady NOT_READY = new NotReady();

    record Ready(Token token) implements TokenState {
    }

    final class NotReady implements TokenState {
    }
}
