package redis.commands;

import redis.storage.Key;
import redis.storage.Storage;

import java.util.List;

public final class Type {

    public static final String NAME = "TYPE";

    private Type() {
    }

    public static Effect execute(List<String> args) {
        if (args.size() != 1) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var key = new Key(args.getFirst());
        var type = Storage.STORAGE.type(key);
        var displayableType = switch (type) {
            case STRING -> "string";
            case LIST -> "list";
            case STREAM -> "stream";
            case NONE -> "none";
        };
        return Messages.simpleMessage(displayableType);
    }
}
