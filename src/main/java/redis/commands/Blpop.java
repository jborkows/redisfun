package redis.commands;

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
        var key = args.getFirst();
        try {
            double howManySecondsToWait = Double.parseDouble(args.get(1));
            if (howManySecondsToWait < 0) {
                return Messages.wrongType("timeout should be 0 or positive.");
            } else if (howManySecondsToWait > 60 * 60) {
                return Messages.wrongType("timeout too big");
            }
            int miliseconds = (int) Math.round(howManySecondsToWait * 1000);
            var aPromise = Storage.STORAGE.blockingPop(key);
            var value = aPromise.work(promise -> switch (miliseconds) {
                case 0 -> promise.get();
                default -> promise.get(miliseconds, TimeUnit.MILLISECONDS);
            });

            LOGGER.info(() -> "Blocking pop operation for " + key + " finished");
            return Messages.bulkMessages(List.of(key, value));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Messages.errorMessage("Error while BLPOP '%s'".formatted(key));
        } catch (ExecutionException e) {
            return Messages.errorMessage("Error while BLPOP '%s'".formatted(key));
        } catch (NumberFormatException e) {
            return Messages.wrongType("timeout should be 0 or positive");
        } catch (TimeoutException e) {
            return Messages.nilArray();
        }
    }
}
