package redis.commands;

import redis.storage.Storage;

import java.util.List;

public final class Type {

    public static final String NAME = "TYPE";

    private Type() {
    }

    public static List<Effect> execute(List<String> args) {
        if (args.size() != 1) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var key = args.getFirst();
        var type = Storage.STORAGE.type(key);
        var displayableType = switch (type) {
            case STRING -> "string";
            case LIST -> "list";
            case NONE -> "none";
        };
        return List.of(Messages.simpleMessage(displayableType));
    }
}
