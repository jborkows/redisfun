package redis;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerBaseIntegrationTest {

    @Test
    void respondsWithPongToPing() throws IOException {
        var response = SharedRedisServer.sendCommand("*1", "$4", "PING");
        assertEquals(List.of("+PONG"), response);
    }

    @Test
    void respondToEcho() throws IOException {
        var response = SharedRedisServer.sendCommand("*2", "$4", "ECHO", "$3", "hey");
        assertEquals(List.of("$3", "hey"), response);
    }

    @Test
    void echoesValuesStartingWithStar() throws IOException {
        var response = SharedRedisServer.sendCommand("*2", "$4", "ECHO", "$4", "*foo");
        assertEquals(List.of("$4", "*foo"), response);
    }

    @Test
    void echoesValuesStartingWithDollar() throws IOException {
        var response = SharedRedisServer.sendCommand("*2", "$4", "ECHO", "$5", "$XXXX");
        assertEquals(List.of("$5", "$XXXX"), response);
    }

    @Test
    void echoesEmptyValue() throws IOException {
        var response = SharedRedisServer.sendCommand("*2", "$4", "ECHO", "$0", "");
        assertEquals(List.of("$0", ""), response);
    }

    @Test
    void echoesHighByteValuesByteForByte() throws IOException {
        var response = SharedRedisServer.sendCommand("*2", "$4", "ECHO", "$1", "é");
        assertEquals(List.of("$1", "é"), response);
    }

    @Test
    void respondsWithPongToPingForMultipleCalls() throws ExecutionException, InterruptedException {
        try (var executors = Executors.newFixedThreadPool(2)) {
            var completionService = new ExecutorCompletionService<List<String>>(executors);
            var latch = new CountDownLatch(1);
            Callable<List<String>> task = () -> {
                latch.await();
                return SharedRedisServer.sendCommand("*1", "$4", "PING");
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
}
