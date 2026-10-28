package redis.commands;

import redis.storage.Key;
import redis.storage.StorageException.OnlyPositiveValues;

import java.util.List;

public final class Lpop {

    public static final String NAME = "LPOP";

    private Lpop() {
    }

    public static void execute(List<String> args, CommandContext context) {
        if (!(args.size() == 1 || args.size() == 2)) {
            context.channel().accept(Messages.wrongNumberOfArguments(NAME));
            return;
        }
        var key = new Key(args.getFirst());
        try {
            var howMany = switch (args.size()) {
                case 2 -> Integer.parseInt(args.get(1));
                default -> 1;
            };
            var element = context.storage().pop(key, howMany);
            if (element == null) {
                if (requestingSingleElementPop(args)) {
                    context.channel().accept(Messages.nilMessage());
                } else {
                    context.channel().accept(Messages.nilArray());
                }
            } else if (element.size() == 1 && requestingSingleElementPop(args)) {
                context.channel().accept(Messages.bulkMessage(element.getFirst()));
            } else {
                context.channel().accept(Messages.bulkMessages(element));
            }
        } catch (NumberFormatException | OnlyPositiveValues e) {
            context.channel().accept(Messages.wrongType("Optional number argument has to be numeric greater or equal than 1"));
        }
    }

    private static boolean requestingSingleElementPop(List<String> args) {
        return args.size() == 1;
    }
}
