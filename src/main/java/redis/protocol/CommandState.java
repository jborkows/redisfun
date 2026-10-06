package redis.protocol;

import java.util.List;

public sealed interface CommandState {

    NotReady NOT_READY = new NotReady();

    record Ready(Command command) implements CommandState {
    }

    final class NotReady implements CommandState {
    }

    record InvalidCommand(List<Token> tokens) implements CommandState {
    }

}
