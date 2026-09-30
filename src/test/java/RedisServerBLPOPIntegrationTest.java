import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerBLPOPIntegrationTest {

    private static final Logger LOGGER = Logger.getLogger(RedisServerBLPOPIntegrationTest.class.getName());

    @Test
    void blockingPopAfterPushShouldReturnImmediately(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("RPUSH %s a".formatted(key));
        var response = SharedRedisServer.run("BLPOP %s 0".formatted(key));
        assertEquals(List.of("*2", "$%d".formatted(key.length()), key, "$1", "a"), response);
    }

    @Test
    void BLPOPShouldWaitForPush(TestInfo testInfo) throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var blpop = new Executor<>(() -> SocketTimeout.with(() -> SharedRedisServer.run("BLPOP %s 0".formatted(key)), 1000));
        var blpopThread = new Thread(blpop);
        blpopThread.start();
        TimeUnit.MILLISECONDS.sleep(100);
        var timeOfRPush = LocalDateTime.now();
        SharedRedisServer.run("RPUSH %s a".formatted(key));
        try {
            blpopThread.join();
        } catch (InterruptedException e) {
            LOGGER.info(() -> "Interrupted while waiting for blpop thread to finish");
        }

        assertEquals(List.of("*2", "$%d".formatted(key.length()), key, "$1", "a"), blpop.result);
        assertTrue(timeOfRPush.isBefore(blpop.finishedAt));
    }

    @Test
    void BLPOPShouldWaitShouldRespectOrder(TestInfo testInfo) throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var countdownLatch = new CountDownLatch(1);
        var blpop1 = new Executor<>(() -> SocketTimeout.with(() -> OnWroteRegister.with(
                () -> {
                    try {
                        return SharedRedisServer.run("BLPOP %s 0".formatted(key));
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "on blpop1", e);
                        return List.of();
                    }
                }, countdownLatch::countDown
        ), 1000));
        var blpop2 = new Executor<>(() -> SocketTimeout.with(() -> {
            try {
                countdownLatch.await();
            } catch (InterruptedException e) {
                LOGGER.log(Level.SEVERE, "on blpop2", e);
            }
            return SharedRedisServer.run("BLPOP %s 0".formatted(key));
        }, 1000));
        var blpop1Thread = new Thread(blpop1);
        var blpop2Thread = new Thread(blpop2);
        blpop1Thread.start();
        blpop2Thread.start();
        TimeUnit.MILLISECONDS.sleep(10);
        SharedRedisServer.run("RPUSH %s a".formatted(key));
        TimeUnit.MILLISECONDS.sleep(10);
        SharedRedisServer.run("RPUSH %s b".formatted(key));
        blpop1Thread.join();
        blpop2Thread.join();

        assertEquals(List.of("*2", "$%d".formatted(key.length()), key, "$1", "a"), blpop1.result);
        assertEquals(List.of("*2", "$%d".formatted(key.length()), key, "$1", "b"), blpop2.result);
        assertTrue(blpop1.finishedAt.isBefore(blpop2.finishedAt));
    }

    @Test
    void ifResponseAfterTimeoutIgnoreIt(TestInfo testInfo) throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var blpop = new Executor<>(() -> SocketTimeout.with(() -> SharedRedisServer.run("BLPOP %s 0.1".formatted(key)), 1000));
        var blpopThread = new Thread(blpop);
        blpopThread.start();
        TimeUnit.MILLISECONDS.sleep(200);
        SharedRedisServer.run("RPUSH %s a".formatted(key));
        try {
            blpopThread.join();
        } catch (InterruptedException e) {
            LOGGER.info(() -> "Interrupted while waiting for blpop thread to finish");
        }
        assertEquals(List.of("*-1"), blpop.result);
    }

    @Test
    void BLPOPCanDecideHowMuchToWaitAndIfResponseReturnsInTimeOk(TestInfo testInfo) throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var blpop = new Executor<>(() -> SocketTimeout.with(() -> SharedRedisServer.run("BLPOP %s 0.1".formatted(key)), 1000));
        var blpopThread = new Thread(blpop);
        blpopThread.start();
        TimeUnit.MILLISECONDS.sleep(10);
        SharedRedisServer.run("RPUSH %s a".formatted(key));
        try {
            blpopThread.join();
        } catch (InterruptedException e) {
            LOGGER.info(() -> "Interrupted while waiting for blpop thread to finish");
        }
        assertEquals(List.of("*2", "$%d".formatted(key.length()), key, "$1", "a"), blpop.result);
    }

    @Test
    void ifResponseAfterTimeoutIgnoreItAndDoNotPerformAction(TestInfo testInfo) throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var blpop = new Executor<>(() -> SocketTimeout.with(() -> SharedRedisServer.run("BLPOP %s 0.1".formatted(key)), 1000));
        var blpopThread = new Thread(blpop);
        blpopThread.start();
        try {
            blpopThread.join();
        } catch (InterruptedException e) {
            LOGGER.info(() -> "Interrupted while waiting for blpop thread to finish");
        }
        TimeUnit.MILLISECONDS.sleep(200);
        SharedRedisServer.run("RPUSH %s a b".formatted(key));
        TimeUnit.MILLISECONDS.sleep(200);

        var response = SharedRedisServer.run("BLPOP %s 0".formatted(key));
        assertEquals(List.of("*2", "$%d".formatted(key.length()), key, "$1", "a"), response);
    }


    static class Executor<V> implements Runnable {
        private final NetworkCall<V> runnable;
        volatile LocalDateTime finishedAt;
        volatile V result;

        private Executor(NetworkCall<V> runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            try {
                result = runnable.get();
                finishedAt = LocalDateTime.now();
            } catch (IOException e) {
                Logger.getLogger(RedisServerBLPOPIntegrationTest.class.getName()).log(Level.SEVERE, "While waiting", e);
                finishedAt = LocalDateTime.now();
            }
        }
    }

}
