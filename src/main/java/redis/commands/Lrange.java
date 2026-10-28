package redis.commands;

import redis.storage.Key;

import java.util.List;

public final class Lrange {

    public static final String NAME = "LRANGE";

    private Lrange() {
    }

    public static void execute(List<String> args, CommandContext context) {
        if (args.size() != 3) {
            context.channel().accept(Messages.wrongNumberOfArguments(NAME));
            return;
        }
        var key = new Key(args.getFirst());
        var start = args.get(1);
        var end = args.get(2);
        try {
            var sublist = context.storage().sublist(key, Integer.parseInt(start), Integer.parseInt(end));
            context.channel().accept(Messages.bulkMessages(sublist));
        } catch (NumberFormatException e) {
            context.channel().accept(Messages.wrongType("Both start and end have to be numbers"));
        }
    }

}
