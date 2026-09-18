import java.util.List;
import java.util.Locale;

class Interpreter {
    static List<Effect> interpret(Command command) {
        var commandName = command.name().toUpperCase(Locale.ROOT);
        return switch (commandName) {
            case "PING" -> List.of(simpleMessage("PONG"));
            case "ECHO" -> echo(command.args());
            case "GET" -> getCommand(command.args());
            case "SET" -> setCommand(command.args());
            default -> List.of();
        };
    }

    private static List<Effect> setCommand(List<String> args) {
        Storage.STORAGE.put(args.get(0), args.get(1));
        return List.of(simpleMessage("OK"));
    }

    private static Effect simpleMessage(String message) {
        return new PrintText("+" + message);
    }

    private static List<Effect> bulkMessage(String message) {
        return List.of(new PrintText("$" + message.length()), new PrintText(message));
    }

    private static Effect nilMessage() {
        return new PrintText("$-1");
    }

    private static List<Effect> getCommand(List<String> args) {
        var found = Storage.STORAGE.get(args.getFirst());
        if (found == null) {
            return List.of(nilMessage());
        } else {
            return bulkMessage(found.toString());
        }
    }

    private static List<Effect> echo(List<String> args) {
        if (args.size() != 1) {
            return List.of(new PrintText("-ERR wrong number of arguments for 'echo' command"));
        }
        var value = args.getFirst();
        return bulkMessage(value);
    }
}
