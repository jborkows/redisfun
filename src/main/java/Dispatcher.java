import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

class Dispatcher {
    private static final Logger logger = Logger.getLogger(Dispatcher.class.getName());

    Dispatching process(byte[] input, int read) {
        logger.fine(() -> "Received: " + new String(input, 0, read));
        return new Dispatching(this, new PrintText("+PONG"));
    }

}

record Dispatching(Dispatcher dispatcher, List<Effect> effects) {
    Dispatching(Dispatcher dispatcher, Effect... effects) {
        this(dispatcher, Arrays.asList(effects));
    }
}
