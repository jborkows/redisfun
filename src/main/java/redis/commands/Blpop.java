package redis.commands;

import redis.storage.Key;

import java.util.List;
import java.util.logging.Logger;

public final class Blpop {
    public static final String NAME = "BLPOP";
    private static final Logger LOGGER = Logger.getLogger(Blpop.class.getName());

    private Blpop() {
    }

    public static void execute(List<String> args, CommandContext context) {
        if (args.size() != 2) {
            context.channel().accept(Messages.wrongNumberOfArguments(NAME));
            return;
        }
        var keyString = args.getFirst();
        var key = new Key(keyString);
        try {
            double howManySecondsToWait = Double.parseDouble(args.get(1));
            if (howManySecondsToWait < 0) {
                context.channel().accept(Messages.wrongType("timeout should be 0 or positive."));
                return;
            } else if (howManySecondsToWait > 60 * 60) {
                context.channel().accept(Messages.wrongType("timeout too big"));
                return;
            }
            int milliseconds = (int) Math.round(howManySecondsToWait * 1000);
            context.storage().blockingPop(
                    key,
                    context.channel(),
                    value -> {
                        LOGGER.info(() -> "Blocking pop operation for " + keyString + " finished");
                        context.channel().accept(Messages.bulkMessages(List.of(keyString, value)));
                    },
                    () -> context.channel().accept(Messages.nilArray()),
                    context.scheduler(),
                    milliseconds
            );
        } catch (NumberFormatException e) {
            context.channel().accept(Messages.wrongType("timeout should be 0 or positive"));
        }
    }
}
