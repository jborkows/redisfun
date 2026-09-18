import java.util.List;

class Interpreter {
    static List<Effect> interpret(Command command) {
        var commandName = command.name().toUpperCase();
        return switch (commandName) {
            case "PING" -> List.of(new PrintText("+PONG"));
            case "ECHO" ->
                    List.of(new PrintText("$" + command.args().getFirst().length()), new PrintText(command.args().getFirst()));
            default -> List.of();
        };
    }
}
