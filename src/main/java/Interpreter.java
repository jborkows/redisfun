import java.util.List;
import java.util.Locale;

class Interpreter {
    static List<Effect> interpret(Command command) {
        var commandName = command.name().toUpperCase(Locale.ROOT);
        return switch (commandName) {
            case "PING" -> List.of(new PrintText("+PONG"));
            case "ECHO" -> echo(command.args());
            default -> List.of();
        };
    }

    private static List<Effect> echo(List<String> args) {
        if (args.size() != 1) {
            return List.of(new PrintText("-ERR wrong number of arguments for 'echo' command"));
        }
        var value = args.getFirst();
        return List.of(new PrintText("$" + value.length()), new PrintText(value));
    }
}
