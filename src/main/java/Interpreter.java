import java.util.List;
import java.util.Locale;

class Interpreter {
    static List<Effect> interpret(Command command) {
        var commandName = command.name().toUpperCase(Locale.ROOT);
        try {
            return switch (commandName) {
                case "PING" -> List.of(Messages.simpleMessage("PONG"));
                case "ECHO" -> echo(command.args());
                case "GET" -> getCommand(command.args());
                case "SET" -> SetCommand.execute(command.args());
                case "RPUSH" -> Rpush.execute(command.args());
                default -> Messages.errorMessage("Unknown command '%s'".formatted(commandName));
            };
        } catch (StorageException storageException) {
            return parseException(commandName, storageException);
        }
    }

    private static List<Effect> parseException(String commandName, StorageException storageException) {
        return switch (storageException) {
            case OperationAllowedOnlyForLists _ ->
                    Messages.errorMessage("Operation %s allowed only for lists".formatted(commandName));
            case OperationAllowedOnlyForSingleElement _ ->
                    Messages.errorMessage("Operation %s allowed only for single element".formatted(commandName));
        };
    }

    private static List<Effect> getCommand(List<String> args) {
        if (args.size() != 1) {
            return Messages.wrongNumberOfArguments("get");
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
            return Messages.wrongNumberOfArguments("echo");
        }
        var value = args.getFirst();
        return Messages.bulkMessage(value);
    }
}
