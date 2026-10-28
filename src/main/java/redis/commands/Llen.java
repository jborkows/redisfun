package redis.commands;

import redis.storage.Key;

import java.util.List;

public final class Llen {

    public static final String NAME = "LLEN";

    private Llen() {
    }

    public static void execute(List<String> args, CommandContext context) {
        if (args.size() != 1) {
            context.channel().accept(Messages.wrongNumberOfArguments(NAME));
            return;
        }
        var key = new Key(args.getFirst());
        int count = context.storage().lengthOf(key);
        context.channel().accept(Messages.respInteger(count));
    }

}
