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
class RedisServerDiscardIntegrationTest {

    @Test
    void discardShouldReturnOkAndAbortTransaction(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        var response = SharedRedisServer.runOnSameConnection(
                "MULTI",
                "SET %s 41".formatted(key),
                "INCR %s".formatted(key),
                "DISCARD",
                "GET %s".formatted(key),
                "DISCARD"
        );
        assertEquals(List.of(
                "+OK",
                "+QUEUED",
                "+QUEUED",
                "+OK",
                "$-1",
                "-ERR DISCARD without MULTI"
        ), response);
    }

    @Test
    void discardInEmptyTransactionShouldReturnOk() throws IOException {
        var response = SharedRedisServer.runOnSameConnection("MULTI", "DISCARD");
        assertEquals(List.of("+OK", "+OK"), response);
    }

    @Test
    void discardWithoutMultiShouldFail() throws IOException {
        var response = SharedRedisServer.run("DISCARD");
        assertEquals(List.of("-ERR DISCARD without MULTI"), response);
    }

    @Test
    void discardWithArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.run("DISCARD extra");
        assertEquals(List.of("-ERR wrong number of arguments for 'DISCARD' command"), response);
    }

    @Test
    void discardClearsWatchStateSoNewTransactionSucceeds(TestInfo testInfo)
            throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var watchedKey1 = key + ".foo";
        var watchedKey2 = key + ".bar";
        var latch = new CountDownLatch(1);
        var transactionResponse = new ArrayList<String>();

        var transactionThread = new Thread(() -> {
            try {
                transactionResponse.addAll(runOnSameConnection(
                        Immediately("SET %s 100".formatted(watchedKey1)),
                        Immediately("SET %s 200".formatted(watchedKey2)),
                        Immediately("WATCH %s %s".formatted(watchedKey1, watchedKey2)),
                        Immediately("MULTI"),
                        Waitable("SET %s 300".formatted(watchedKey2), () -> {
                            if (!latch.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("Timed out waiting for latch");
                            }
                        }),
                        Immediately("DISCARD"),
                        Immediately("MULTI"),
                        Immediately("SET %s 300".formatted(watchedKey2)),
                        Immediately("EXEC")
                ));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        transactionThread.start();

        TimeUnit.MILLISECONDS.sleep(50);
        SharedRedisServer.run("SET %s 400".formatted(watchedKey1));
        latch.countDown();
        transactionThread.join();

        assertEquals(List.of("+OK", "+OK", "+OK", "+OK", "+QUEUED", "+OK", "+OK", "+QUEUED", "*1", "+OK"),
                transactionResponse);
        var writtenValue = SharedRedisServer.run("GET %s".formatted(watchedKey2));
        assertEquals(List.of("$3", "300"), writtenValue);
    }
}
