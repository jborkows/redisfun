package redis.commands;

import java.util.List;

final class Messages {

    private Messages() {
    }

    static Effect respInteger(long value) {
        return new Effect.Integer(value);
    }

    static Effect simpleMessage(String message) {
        return new Effect.SimpleString(message);
    }

    static List<Effect> bulkMessage(String message) {
        return List.of(new Effect.BulkString(message));
    }

    static List<Effect> bulkMessages(List<String> messages) {
        return List.of(new Effect.Array(messages.stream().<Effect>map(Effect.BulkString::new).toList()));
    }

    static Effect nilMessage() {
        return new Effect.NullBulk();
    }

    static Effect nilArray() {
        return new Effect.NullArray();
    }

    static List<Effect> errorMessage(String message) {
        return List.of(new Effect.Error(message));
    }

    static List<Effect> wrongType(String message) {
        return List.of(new Effect.WrongType(message));
    }

    static List<Effect> wrongNumberOfArguments(String commandName) {
        return errorMessage("wrong number of arguments for '" + commandName + "' command");
    }

    static List<Effect> syntaxError() {
        return errorMessage("syntax error");
    }

    static List<Effect> array(Effect... effects) {
        return List.of(new Effect.Array(List.of(effects)));
    }
}
