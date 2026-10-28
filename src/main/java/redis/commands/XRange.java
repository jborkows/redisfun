package redis.commands;

import redis.storage.Key;
import redis.storage.StorageException;

import java.util.List;

public final class XRange {

    public static final String NAME = "XRANGE";

    private XRange() {
    }

    public static void execute(List<String> args, CommandContext context) {
        if (args.size() != 3) {
            context.channel().accept(Messages.wrongNumberOfArguments(NAME));
            return;
        }

        var key = new Key(args.get(0));
        var start = args.get(1);
        var end = args.get(2);

        try {
            var result = context.storage().range(key, start, end);
            context.channel().accept(ReadStorageEntries.readEntries(result));
        } catch (StorageException.StreamOperationsException.InvalidId e) {
            context.channel().accept(Messages.wrongType("Used wrong ids"));
        }
    }
}
