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

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerExecIntegrationTest {

    @Test
    void execAbortsTransactionWhenWatchedKeyIsModifiedByAnotherClient(TestInfo testInfo)
            throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var watchedKey = key + ".foo";
        var writtenKey = key + ".bar";
        var latch = new CountDownLatch(1);
        var transactionResponse = new ArrayList<String>();

        var transactionThread = new Thread(() -> {
            try {
                transactionResponse.addAll(runOnSameConnection(
                        Immediately("SET %s 100".formatted(watchedKey)),
                        Immediately("SET %s 200".formatted(writtenKey)),
                        Immediately("WATCH %s".formatted(watchedKey)),
                        Immediately("MULTI"),
                        Waitable("SET %s 300".formatted(writtenKey), () -> {
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
        SharedRedisServer.run("SET %s 200".formatted(watchedKey));
        latch.countDown();
        transactionThread.join();

        assertEquals(List.of("+OK", "+OK", "+OK", "+OK", "+QUEUED", "*-1"), transactionResponse);
        var writtenValue = SharedRedisServer.run("GET %s".formatted(writtenKey));
        assertEquals(List.of("$3", "200"), writtenValue);
    }

    @Test
    void execSucceedsWhenWatchedKeyIsNotModifiedByAnotherClient(TestInfo testInfo)
            throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var watchedKey = key + ".baz";
        var writtenKey = key + ".caz";
        var latch = new CountDownLatch(1);
        var transactionResponse = new ArrayList<String>();

        var transactionThread = new Thread(() -> {
            try {
                transactionResponse.addAll(runOnSameConnection(
                        Immediately("SET %s 100".formatted(watchedKey)),
                        Immediately("SET %s 200".formatted(writtenKey)),
                        Immediately("WATCH %s".formatted(watchedKey)),
                        Immediately("MULTI"),
                        Waitable("SET %s 400".formatted(writtenKey), () -> {
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
        SharedRedisServer.run("SET %s 300".formatted(writtenKey));
        latch.countDown();
        transactionThread.join();

        assertEquals(List.of("+OK", "+OK", "+OK", "+OK", "+QUEUED", "*1", "+OK"), transactionResponse);
        var writtenValue = SharedRedisServer.run("GET %s".formatted(writtenKey));
        assertEquals(List.of("$3", "400"), writtenValue);
    }

    @Test
    void execAbortsWhenWatchedKeyIsTouchedEvenIfValueIsRestored(TestInfo testInfo)
            throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var watchedKey = key + ".foo";
        var writtenKey = key + ".bar";
        var latch = new CountDownLatch(1);
        var transactionResponse = new ArrayList<String>();

        var transactionThread = new Thread(() -> {
            try {
                transactionResponse.addAll(runOnSameConnection(
                        Immediately("SET %s 100".formatted(watchedKey)),
                        Immediately("WATCH %s".formatted(watchedKey)),
                        Immediately("MULTI"),
                        Waitable("SET %s 200".formatted(writtenKey), () -> {
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
        SharedRedisServer.run("SET %s 200".formatted(watchedKey));
        SharedRedisServer.run("SET %s 100".formatted(watchedKey));
        latch.countDown();
        transactionThread.join();

        assertEquals(List.of("+OK", "+OK", "+OK", "+QUEUED", "*-1"), transactionResponse);
        var writtenValue = SharedRedisServer.run("GET %s".formatted(writtenKey));
        assertEquals(List.of("$-1"), writtenValue);
    }

    @Test
    void execClearsWatchStateAfterAbortedTransaction(TestInfo testInfo) throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var watchedKey = key + ".foo";
        var writtenKey = key + ".bar";
        var latch = new CountDownLatch(1);
        var transactionResponse = new ArrayList<String>();

        var transactionThread = new Thread(() -> {
            try {
                transactionResponse.addAll(runOnSameConnection(
                        Immediately("SET %s 100".formatted(watchedKey)),
                        Immediately("WATCH %s".formatted(watchedKey)),
                        Immediately("MULTI"),
                        Waitable("SET %s 200".formatted(writtenKey), () -> {
                            if (!latch.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("Timed out waiting for latch");
                            }
                        }),
                        Immediately("EXEC"),
                        Immediately("MULTI"),
                        Immediately("SET %s 300".formatted(writtenKey)),
                        Immediately("EXEC")
                ));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        transactionThread.start();

        TimeUnit.MILLISECONDS.sleep(50);
        SharedRedisServer.run("SET %s 200".formatted(watchedKey));
        latch.countDown();
        transactionThread.join();

        assertEquals(List.of("+OK", "+OK", "+OK", "+QUEUED", "*-1", "+OK", "+QUEUED", "*1", "+OK"),
                transactionResponse);
        var writtenValue = SharedRedisServer.run("GET %s".formatted(writtenKey));
        assertEquals(List.of("$3", "300"), writtenValue);
    }
}
