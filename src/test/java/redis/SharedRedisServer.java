package redis;

import org.junit.jupiter.api.TestInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single Redis server instance shared by every integration test class in this
 * directory. The server is started lazily on first use and stopped by a JVM
 * shutdown hook, so all integration tests running in the same Surefire fork
 * talk to the same server.
 */
final class SharedRedisServer {

    private static final Logger LOGGER = Logger.getLogger(SharedRedisServer.class.getName());
    private static final Semaphore MAX_CONCURRENT_SOCKETS = new Semaphore(50);
    private static RedisServer server;

    private SharedRedisServer() {
    }

    /**
     * Unique key for a test, derived from the test class and method name
     * (e.g. "RedisServerLLenIntegrationTest.llenForNotExistingListShouldReturnZero").
     * Because the server is shared by all integration test classes in the same
     * JVM fork, class + method is the smallest scope that guarantees no two
     * tests ever operate on the same key.
     */
    static String key(TestInfo testInfo) {
        return testInfo.getTestClass().orElseThrow().getSimpleName()
                + "." + testInfo.getTestMethod().orElseThrow().getName();
    }

    static synchronized int port() {
        if (server == null) {
            Logging.configureFromSystemProperty();
            // Random free port, never the application default (6379)
            server = RedisServer.onRandomPort();
            try {
                server.start();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to start the shared Redis server", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(SharedRedisServer::stopQuietly, "shared-redis-server-shutdown"));
        }
        return server.getPort();
    }

    private static void stopQuietly() {
        try {
            server.close();
        } catch (IOException ignored) {
            // shutting down anyway
        }
    }

    static List<String> run(String command) throws IOException {
        return cli(command.split(" ", -1));
    }

    static List<String> cli(String... command) throws IOException {
        var commandToSend = new ArrayList<String>();
        commandToSend.add("*" + command.length);
        for (var commandLine : command) {
            commandToSend.add("$" + commandLine.length());
            commandToSend.add(commandLine);
        }
        return sendCommand(commandToSend.toArray(new String[0]));
    }

    static List<String> sendCommand(String... command) throws IOException {
        try {
            MAX_CONCURRENT_SOCKETS.acquire();
            try (var socket = new Socket("localhost", port())) {
                // Bound the blocking read so a short/incomplete response fails the test
                // instead of freezing it (readLine() can never be unblocked otherwise).
                socket.setSoTimeout(SocketTimeout.timeoutMs());
                for (var cmd : command) {
                    socket.getOutputStream().write((cmd + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                }
                socket.getOutputStream().flush();
                OnWroteRegister.socketWroteData();

                var response = new ByteArrayOutputStream();
                var buffer = new byte[1024];
                var in = socket.getInputStream();
                try {
                    LOGGER.log(Level.FINER, "Starting reading socket after command " + Arrays.toString(command));
                    while (true) {
                        var read = in.read(buffer);
                        if (read == -1) {
                            break;
                        }
                        response.write(buffer, 0, read);
                    }
                } catch (SocketTimeoutException e) {
                    LOGGER.log(Level.SEVERE, "Socket timed out after command " + Arrays.toString(command), e);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Some other error while reading after command " + Arrays.toString(command), e);
                }

                var text = response.toString(StandardCharsets.ISO_8859_1);
                if (text.isEmpty()) {
                    return List.of();
                }
                var lines = new ArrayList<>(Arrays.asList(text.split("\r\n", -1)));
                // The response always ends with CRLF, which leaves one trailing
                // empty element behind; drop only that one.
                if (!lines.isEmpty() && lines.getLast().isEmpty()) {
                    lines.removeLast();
                }
                return lines;
            } finally {
                MAX_CONCURRENT_SOCKETS.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
