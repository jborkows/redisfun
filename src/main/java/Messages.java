import java.util.List;

final class Messages {

    private Messages() {
    }

    static Effect respInteger(long value) {
        return new PrintText(":" + value);
    }

    static Effect simpleMessage(String message) {
        return new PrintText("+" + message);
    }

    static List<Effect> bulkMessage(String message) {
        return List.of(new PrintText("$" + message.length()), new PrintText(message));
    }

    static Effect nilMessage() {
        return new PrintText("$-1");
    }

    static List<Effect> errorMessage(String message) {
        return List.of(new PrintText("-ERR " + message));
    }

    static List<Effect> wrongType(String message) {
        return List.of(new PrintText("-WRONGTYPE " + message));
    }

    static List<Effect> wrongNumberOfArguments(String commandName) {
        return errorMessage("wrong number of arguments for '" + commandName + "' command");
    }

    static List<Effect> syntaxError() {
        return errorMessage("syntax error");
    }
}
