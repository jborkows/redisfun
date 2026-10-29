package redis;

import redis.commands.Effect;
import redis.commands.EffectChannel;
import redis.commands.Interpreter;
import redis.protocol.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

class Dispatcher implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(Dispatcher.class.getName());
    private final Interpreter interpreter;

    Dispatcher() {
        this.interpreter = new Interpreter();
    }

    ConnectionHandle openConnection(ServerKind serverKind) {
        var context = new ConnectionContext(serverKind);
        context.addCloseHook(() -> interpreter.onConnectionClosed(context.channel()));
        return new ConnectionHandle(this, context);
    }

    void process(byte[] input, int read, ConnectionContext context) {
        logger.fine(() -> "Received: " + new String(input, 0, read));
        var commands = parseTokens(extractTokens(input, read, context), context);
        for (var command : commands) {
            interpreter.apply(command, context.channel, context.serverKind);
            try {
                var effect = context.queue.take();
                context.outputStream.write(effect.text().getBytes(StandardCharsets.ISO_8859_1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                logger.severe("IOException: " + e.getMessage());
                break;
            }
        }
        try {
            context.outputStream.flush();
        } catch (IOException e) {
            logger.severe("IOException: " + e.getMessage());
        }
    }

    private List<Command> parseTokens(List<Token> tokens, ConnectionContext context) {
        var commands = new ArrayList<Command>();
        for (var token : tokens) {
            var parsingResult = context.parser.on(token);
            if (parsingResult.state() instanceof CommandState.Ready(Command command)) {
                commands.add(command);
            }
            context.parser = parsingResult.parser();
        }
        return List.copyOf(commands);
    }

    private List<Token> extractTokens(byte[] input, int read, ConnectionContext context) {
        var tokens = new ArrayList<Token>();
        for (int index = 0; index < read; index++) {
            var tokenizerResponse = context.lexer.onByte(input[index]);
            if (tokenizerResponse.state() instanceof TokenState.Ready(Token token)) {
                tokens.add(token);
            }
            context.lexer = tokenizerResponse.lexer();
        }
        return tokens;
    }

    record ConnectionHandle(Dispatcher dispatcher, ConnectionContext context) implements AutoCloseable {
        @Override
        public void close() {
            context.close();
        }
    }

    static final class ConnectionContext implements AutoCloseable {
        private OutputStream outputStream;
        private Lexer lexer = new Lexer();
        private Parser parser = new Parser();
        private final BlockingQueue<Effect> queue = new LinkedBlockingQueue<>();
        private final List<Runnable> closeHooks = new ArrayList<>();
        private final ServerKind serverKind;

        private final EffectChannel channel = effect -> {
            if (!queue.offer(effect)) {
                throw new IllegalStateException("Effect queue is full");
            }
        };

        ConnectionContext(ServerKind serverKind) {
            this.serverKind = serverKind;
        }

        void bind(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        EffectChannel channel() {
            return channel;
        }

        void addCloseHook(Runnable hook) {
            closeHooks.add(hook);
        }

        @Override
        public void close() {
            for (var hook : closeHooks) {
                try {
                    hook.run();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Connection close hook failed", e);
                }
            }
        }
    }

    @Override
    public void close() {
        interpreter.close();
    }
}
