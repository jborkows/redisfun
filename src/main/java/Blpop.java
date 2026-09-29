import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

final class Blpop {
    static final String NAME = "BLPOP";
    private static final Logger LOGGER = Logger.getLogger(Blpop.class.getName());

    private Blpop() {
    }

    static List<Effect> execute(List<String> args) {
        if (args.size() != 2) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var key = args.getFirst();
        try {
            var value = Storage.STORAGE.blockingPop(key).get();
            LOGGER.info(() -> "Blocking pop operation for " + key + " finished");
            return Messages.bulkMessages(List.of(key, value));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Messages.errorMessage("Error while BLPOP '%s'".formatted(key));
        } catch (ExecutionException e) {
            return Messages.errorMessage("Error while BLPOP '%s'".formatted(key));
        }
    }
}
