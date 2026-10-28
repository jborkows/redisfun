package redis.commands;

import redis.storage.Key;
import redis.storage.StorageException;

import java.util.List;

public final class Incr {

    public static final String NAME = "INCR";

    private Incr() {
    }

    public static void execute(List<String> args, CommandContext context) {
        if (args.size() != 1) {
            context.channel().accept(Messages.wrongNumberOfArguments(NAME));
            return;
        }
        var key = new Key(args.getFirst());

        try {
            var value = context.storage().increment(key);
            context.channel().accept(Messages.respInteger(value));
        } catch (StorageException.OperationAllowedOnlyForIntegers e) {
            context.channel().accept(Messages.errorMessage("value is not an integer or out of range"));
        }

    }

}
