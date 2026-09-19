import java.util.List;

final class Messages {

    private Messages() {
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

    static List<Effect> errorMessage(String commandName) {
        return List.of(new PrintText("-ERR wrong number of arguments for '" + commandName + "' command"));
    }

    static List<Effect> syntaxError() {
        return List.of(new PrintText("-ERR syntax error"));
    }
}
