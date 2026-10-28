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
class RedisServerUnwatchIntegrationTest {

    @Test
    void unwatchWithoutWatchedKeysShouldReturnOk() throws IOException {
        var response = SharedRedisServer.run("UNWATCH");
        assertEquals(List.of("+OK"), response);
    }

    @Test
    void unwatchWithWatchedKeysShouldReturnOk(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        var response = SharedRedisServer.runOnSameConnection(
                "WATCH %s".formatted(key),
                "UNWATCH"
        );
        assertEquals(List.of("+OK", "+OK"), response);
    }

    @Test
    void unwatchClearsWatchStateSoTransactionSucceedsAfterKeyIsModified(TestInfo testInfo)
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
                        Waitable("UNWATCH", () -> {
                            if (!latch.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("Timed out waiting for latch");
                            }
                        }),
                        Immediately("MULTI"),
                        Immediately("SET %s 400".formatted(writtenKey)),
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

        assertEquals(List.of("+OK", "+OK", "+OK", "+OK", "+OK", "+QUEUED", "*1", "+OK"), transactionResponse);
        var writtenValue = SharedRedisServer.run("GET %s".formatted(writtenKey));
        assertEquals(List.of("$3", "400"), writtenValue);
    }

    @Test
    void unwatchInsideMultiShouldReturnError(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        var response = SharedRedisServer.runOnSameConnection(
                "MULTI",
                "UNWATCH"
        );
        assertEquals(List.of("+OK", "-ERR UNWATCH inside MULTI is not allowed"), response);
    }
}
