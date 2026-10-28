package redis;

import redis.commands.Effect;
import redis.commands.Interpreter;
import redis.protocol.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

class Dispatcher implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(Dispatcher.class.getName());
    private final Interpreter interpreter;
    private Lexer lexer = new Lexer();
    private Parser parser = new Parser();

    Dispatcher() {
        this.interpreter = new Interpreter();
    }

    List<Effect> process(byte[] input, int read) {
        logger.fine(() -> "Received: " + new String(input, 0, read));
        var tokens = extractTokens(input, read);
        var commands = parseTokens(tokens);
        var effects = new ArrayList<Effect>();
        for (var command : commands) {
            interpreter.apply(command, effects::add);
        }
        return List.copyOf(effects);
    }

    private List<Command> parseTokens(List<Token> tokens) {
        var commands = new ArrayList<Command>();
        for (var token : tokens) {
            var parsingResult = parser.on(token);
            if (parsingResult.state() instanceof CommandState.Ready(Command command)) {
                commands.add(command);
            }
            parser = parsingResult.parser();
        }
        return List.copyOf(commands);
    }

    private List<Token> extractTokens(byte[] input, int read) {
        var tokens = new ArrayList<Token>();
        for (int index = 0; index < read; index++) {
            var tokenizerResponse = lexer.onByte(input[index]);
            if (tokenizerResponse.state() instanceof TokenState.Ready(Token token)) {
                tokens.add(token);
            }
            lexer = tokenizerResponse.lexer();
        }
        return tokens;
    }


    @Override
    public void close() {
        interpreter.close();
    }
}
