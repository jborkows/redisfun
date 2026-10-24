package redis;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static redis.SharedRedisServer.Immediately;
import static redis.SharedRedisServer.Waitable;
import static redis.SharedRedisServer.runOnSameConnection;

/**
 * Tests for the {@link SharedRedisServer} {@link SharedRedisServer.CommandInput}
 * infrastructure itself — in particular the {@code Waitable} input that lets a
 * test pause mid-connection to orchestrate timing across connections.
 */
@TestMethodOrder(MethodOrderer.Random.class)
class SharedRedisServerCommandInputIntegrationTest {

    @Test
    void waitableCommandHoldsConnectionUntilReleased(TestInfo testInfo) throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var latch = new CountDownLatch(1);
        var transactionResponse = new ArrayList<String>();

        var transactionThread = new Thread(() -> {
            try {
                transactionResponse.addAll(runOnSameConnection(
                        Immediately("MULTI"),
                        Waitable("SET %s 1".formatted(key), () -> {
                            if (!latch.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("Timed out waiting for latch");
                            }
                        }),
                        Immediately("EXEC")
                ));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        transactionThread.start();

        TimeUnit.MILLISECONDS.sleep(50);
        var duringWait = SharedRedisServer.run("GET %s".formatted(key));
        assertEquals(List.of("$-1"), duringWait);

        latch.countDown();
        transactionThread.join();

        assertEquals(List.of("+OK", "+QUEUED", "*1", "+OK"), transactionResponse);
        var afterExec = SharedRedisServer.run("GET %s".formatted(key));
        assertEquals(List.of("$1", "1"), afterExec);
    }

    @Test
    void waitableCommandHoldsConnectionForMultipleKeys(TestInfo testInfo) throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var firstKey = key + ".first";
        var secondKey = key + ".second";
        var latch = new CountDownLatch(1);
        var transactionResponse = new ArrayList<String>();

        var transactionThread = new Thread(() -> {
            try {
                transactionResponse.addAll(runOnSameConnection(
                        Immediately("MULTI"),
                        Waitable("SET %s 1".formatted(firstKey), () -> {
                            if (!latch.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("Timed out waiting for latch");
                            }
                        }),
                        Immediately("SET %s 2".formatted(secondKey)),
                        Immediately("EXEC")
                ));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        transactionThread.start();

        TimeUnit.MILLISECONDS.sleep(50);
        var firstDuringWait = SharedRedisServer.run("GET %s".formatted(firstKey));
        var secondDuringWait = SharedRedisServer.run("GET %s".formatted(secondKey));
        assertEquals(List.of("$-1"), firstDuringWait);
        assertEquals(List.of("$-1"), secondDuringWait);

        latch.countDown();
        transactionThread.join();

        assertEquals(List.of("+OK", "+QUEUED", "+QUEUED", "*2", "+OK", "+OK"), transactionResponse);
        var firstAfterExec = SharedRedisServer.run("GET %s".formatted(firstKey));
        var secondAfterExec = SharedRedisServer.run("GET %s".formatted(secondKey));
        assertEquals(List.of("$1", "1"), firstAfterExec);
        assertEquals(List.of("$1", "2"), secondAfterExec);
    }
}
