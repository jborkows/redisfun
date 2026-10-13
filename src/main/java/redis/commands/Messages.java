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

    static Effect bulkMessage(String message) {
        return new Effect.BulkString(message);
    }

    static Effect bulkMessages(List<String> messages) {
        return new Effect.Array(messages.stream().<Effect>map(Effect.BulkString::new).toList());
    }

    static Effect nilMessage() {
        return new Effect.NullBulk();
    }

    static Effect nilArray() {
        return new Effect.NullArray();
    }

    static Effect errorMessage(String message) {
        return new Effect.Error(message);
    }

    static Effect wrongType(String message) {
        return new Effect.WrongType(message);
    }

    static Effect wrongNumberOfArguments(String commandName) {
        return errorMessage("wrong number of arguments for '" + commandName + "' command");
    }

    static Effect syntaxError() {
        return errorMessage("syntax error");
    }

    static Effect array(Effect... effects) {
        return new Effect.Array(List.of(effects));
    }

    static Effect array(List<Effect> effects) {
        return new Effect.Array(effects);
    }
}
