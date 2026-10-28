package redis.commands;

import redis.storage.Key;

import java.util.List;

public final class Type {

    public static final String NAME = "TYPE";

    private Type() {
    }

    public static void execute(List<String> args, CommandContext context) {
        if (args.size() != 1) {
            context.channel().accept(Messages.wrongNumberOfArguments(NAME));
            return;
        }
        var key = new Key(args.getFirst());
        var type = context.storage().type(key);
        var displayableType = switch (type) {
            case STRING -> "string";
            case LIST -> "list";
            case STREAM -> "stream";
            case NONE -> "none";
        };
        context.channel().accept(Messages.simpleMessage(displayableType));
    }
}
