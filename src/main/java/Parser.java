import java.util.ArrayList;
import java.util.List;

sealed interface CommandState {

    NotReady NOT_READY = new NotReady();

    record Ready(Command command) implements CommandState {
    }

    final class NotReady implements CommandState {
    }

    record InvalidCommand(List<Token> tokens) implements CommandState {
    }

}

record Command(String name, List<String> args) {
}

public class Parser {

    private enum Phase {
        START, ARRAY_LENGTH, DOLLAR, ELEMENT_LENGTH, ELEMENT_TEXT
    }

    private final List<Token> commandTokens = new ArrayList<>();
    private final List<String> elements = new ArrayList<>();
    private Phase phase = Phase.START;
    private int expectedElements = 0;
    private int declaredElementLength = 0;

    CommandResult on(Token token) {
        commandTokens.add(token);
        switch (phase) {
            case START -> {
                if (token instanceof Star) {
                    phase = Phase.ARRAY_LENGTH;
                    return notReady();
                }
                return invalid();
            }
            case ARRAY_LENGTH -> {
                if (token instanceof Length(int n) && n > 0) {
                    expectedElements = n;
                    phase = Phase.DOLLAR;
                    return notReady();
                }
                return invalid();
            }
            case DOLLAR -> {
                if (token instanceof Dollar) {
                    phase = Phase.ELEMENT_LENGTH;
                    return notReady();
                }
                return invalid();
            }
            case ELEMENT_LENGTH -> {
                if (token instanceof Length(int n)) {
                    declaredElementLength = n;
                    phase = Phase.ELEMENT_TEXT;
                    return notReady();
                }
                return invalid();
            }
            case ELEMENT_TEXT -> {
                if (token instanceof Text(String value) && value.length() == declaredElementLength) {
                    elements.add(value);
                    if (elements.size() == expectedElements) {
                        return ready(command());
                    }
                    phase = Phase.DOLLAR;
                    return notReady();
                }
                return invalid();
            }
        }
        throw new IllegalStateException("Unexpected phase: " + phase);
    }

    private Command command() {
        return new Command(elements.getFirst(), List.copyOf(elements.subList(1, elements.size())));
    }

    private CommandResult ready(Command command) {
        reset();
        return new CommandResult(this, new CommandState.Ready(command));
    }

    private CommandResult invalid() {
        var invalidCommand = new CommandState.InvalidCommand(List.copyOf(commandTokens));
        reset();
        return new CommandResult(this, invalidCommand);
    }

    private CommandResult notReady() {
        return new CommandResult(this, CommandState.NOT_READY);
    }

    private void reset() {
        commandTokens.clear();
        elements.clear();
        expectedElements = 0;
        declaredElementLength = 0;
        phase = Phase.START;
    }
}

record CommandResult(Parser parser, CommandState state) {
}
