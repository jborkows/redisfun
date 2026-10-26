package redis.commands;

import redis.storage.Key;
import redis.storage.Storage;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public final class Blpop {
    public static final String NAME = "BLPOP";
    private static final Logger LOGGER = Logger.getLogger(Blpop.class.getName());

    private Blpop() {
    }

    public static Effect execute(List<String> args) {
        if (args.size() != 2) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var keyString = args.getFirst();
        var key = new Key(keyString);
        try {
            double howManySecondsToWait = Double.parseDouble(args.get(1));
            if (howManySecondsToWait < 0) {
                return Messages.wrongType("timeout should be 0 or positive.");
            } else if (howManySecondsToWait > 60 * 60) {
                return Messages.wrongType("timeout too big");
            }
            int milliseconds = (int) Math.round(howManySecondsToWait * 1000);
            var aPromise = Storage.STORAGE.blockingPop(key);
            var value = aPromise.work(promise -> switch (milliseconds) {
                case 0 -> promise.get();
                default -> promise.get(milliseconds, TimeUnit.MILLISECONDS);
            });

            LOGGER.info(() -> "Blocking pop operation for " + keyString + " finished");
            return Messages.bulkMessages(List.of(keyString, value));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Messages.errorMessage("Error while BLPOP '%s'".formatted(keyString));
        } catch (ExecutionException e) {
            return Messages.errorMessage("Error while BLPOP '%s'".formatted(keyString));
        } catch (NumberFormatException e) {
            return Messages.wrongType("timeout should be 0 or positive");
        } catch (TimeoutException e) {
            return Messages.nilArray();
        }
    }
}
