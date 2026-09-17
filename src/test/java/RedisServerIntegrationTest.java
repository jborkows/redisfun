import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
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
        assertEquals("+PONG", response);
    }

    @Test
    void respondsWithPongToPingForMultipleCalls() throws ExecutionException, InterruptedException {
        try (var executors = Executors.newFixedThreadPool(2)) {
            CompletionService<String> completionService = new ExecutorCompletionService<>(executors);
            var latch = new CountDownLatch(1);
            Callable<String> task = () -> {
                latch.await();
                return sendCommand("*1", "$4", "PING");
            };
            completionService.submit(task);
            completionService.submit(task);
            latch.countDown();
            var first = completionService.take().get();
            var second = completionService.take().get();
            assertEquals("+PONG", first);
            assertEquals("+PONG", second);
        }
    }

    private String sendCommand(String... command) throws IOException {
        try (var socket = new Socket("localhost", server.getPort());
             var in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            for (var cmd : command) {
                socket.getOutputStream().write((cmd + "\r\n").getBytes());
            }
            socket.getOutputStream().flush();
            return in.readLine();
        }
    }
}
