package redis.commands;

import redis.storage.Key;

import java.util.List;

public final class Lpush {

    public static final String NAME = "LPUSH";

    private Lpush() {
    }

    public static void execute(List<String> args, CommandContext context) {
        if (args.size() < 2) {
            context.channel().accept(Messages.wrongNumberOfArguments(NAME));
            return;
        }
        var key = new Key(args.getFirst());
        var count = context.storage().prependElements(key, args.subList(1, args.size()));
        context.channel().accept(Messages.respInteger(count));
    }
}
