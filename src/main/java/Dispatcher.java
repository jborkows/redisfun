import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

class Dispatcher {
    private static final Logger logger = Logger.getLogger(Dispatcher.class.getName());
    private Lexer lexer = new Lexer();
    private Parser parser = new Parser();

    List<Effect> process(byte[] input, int read) {
        logger.fine(() -> "Received: " + new String(input, 0, read));
        var effects = new ArrayList<Effect>();
        for (int index = 0; index < read; index++) {
            var tokenizerResponse = lexer.onByte(input[index]);
            if (tokenizerResponse.state() instanceof TokenState.Ready ready) {
                var parsingResult = parser.on(ready.token());
                if (parsingResult.state() instanceof CommandState.Ready readyCommand) {
                    effects.addAll(Interpreter.interpret(readyCommand.command()));
                }
            }
            lexer = tokenizerResponse.lexer();
        }

        return List.copyOf(effects);
    }
}
