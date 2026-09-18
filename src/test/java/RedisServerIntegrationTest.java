import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisServerIntegrationTest {

    private static RedisServer server;

    @BeforeAll
    static void startServer() throws IOException {
        Logging.configureFromSystemProperty();
        // Random free port, never the application default (6379)
        server = RedisServer.onRandomPort();
        server.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void respondsWithPongToPing() throws IOException {
        var response = sendCommand("*1", "$4", "PING");
        assertEquals(List.of("+PONG"), response);
    }

    @Test
    void respondToEcho() throws IOException {
        var response = sendCommand("*2", "$4", "ECHO", "$3", "hey");
        assertEquals(List.of("$3", "hey"), response);
    }

    @Test
    void respondsWithPongToPingForMultipleCalls() throws ExecutionException, InterruptedException {
        try (var executors = Executors.newFixedThreadPool(2)) {
            CompletionService<List<String>> completionService = new ExecutorCompletionService<>(executors);
            var latch = new CountDownLatch(1);
            Callable<List<String>> task = () -> {
                latch.await();
                return sendCommand("*1", "$4", "PING");
            };
            completionService.submit(task);
            completionService.submit(task);
            latch.countDown();
            var first = completionService.take().get();
            var second = completionService.take().get();
            assertEquals(List.of("+PONG"), first);
            assertEquals(List.of("+PONG"), second);
        }
    }

    private List<String> sendCommand(String... command) throws IOException {
        try (var socket = new Socket("localhost", server.getPort())) {
            // Bound the blocking read so a short/incomplete response fails the test
            // instead of freezing it (readLine() can never be unblocked otherwise).
            socket.setSoTimeout(300);
            for (var cmd : command) {
                socket.getOutputStream().write((cmd + "\r\n").getBytes());
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

            var text = response.toString(StandardCharsets.UTF_8);
            if (text.isEmpty()) {
                return List.of();
            }
            return Arrays.asList(text.split("\r\n"));
        }
    }
}
