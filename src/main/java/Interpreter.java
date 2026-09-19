import java.util.List;
import java.util.Locale;

class Interpreter {
    static List<Effect> interpret(Command command) {
        var commandName = command.name().toUpperCase(Locale.ROOT);
        return switch (commandName) {
            case "PING" -> List.of(Messages.simpleMessage("PONG"));
            case "ECHO" -> echo(command.args());
            case "GET" -> getCommand(command.args());
            case "SET" -> SetCommand.execute(command.args());
            default -> List.of();
        };
    }

    private static List<Effect> getCommand(List<String> args) {
        if (args.size() != 1) {
            return Messages.errorMessage("get");
        }
        var found = Storage.STORAGE.get(args.getFirst());
        if (found == null) {
            return List.of(Messages.nilMessage());
        } else {
            return Messages.bulkMessage(found.toString());
        }
    }

    private static List<Effect> echo(List<String> args) {
        if (args.size() != 1) {
            return Messages.errorMessage("echo");
        }
        var value = args.getFirst();
        return Messages.bulkMessage(value);
    }
}
