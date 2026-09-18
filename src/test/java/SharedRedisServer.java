import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Single Redis server instance shared by every integration test class in this
 * directory. The server is started lazily on first use and stopped by a JVM
 * shutdown hook, so all integration tests running in the same Surefire fork
 * talk to the same server.
 */
final class SharedRedisServer {

    private static RedisServer server;

    private SharedRedisServer() {
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

    static List<String> sendCommand(String... command) throws IOException {
        try (var socket = new Socket("localhost", port())) {
            // Bound the blocking read so a short/incomplete response fails the test
            // instead of freezing it (readLine() can never be unblocked otherwise).
            socket.setSoTimeout(300);
            for (var cmd : command) {
                socket.getOutputStream().write((cmd + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            }
            socket.getOutputStream().flush();

            var response = new ByteArrayOutputStream();
            var buffer = new byte[1024];
            var in = socket.getInputStream();
            try {
                while (true) {
                    var read = in.read(buffer);
                    if (read == -1) {
                        break;
                    }
                    response.write(buffer, 0, read);
                }
            } catch (SocketTimeoutException ignored) {
                // server stopped sending; evaluate what has been received
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
        }
    }
}
