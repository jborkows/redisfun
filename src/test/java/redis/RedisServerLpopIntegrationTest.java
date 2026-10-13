package redis;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerLpopIntegrationTest {

    @Test
    void lpopForEmptyListShouldReturnNullBulkString(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("LPOP %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("$-1"), response);
    }

    @Test
    void lpopShouldTakeFirstElement(TestInfo testInfo) throws IOException {
        SharedRedisServer.run("RPUSH %s a b c".formatted(SharedRedisServer.key(testInfo)));
        var response = SharedRedisServer.run("LPOP %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("$1", "a"), response);
    }

    @Test
    void afterLpopListShouldBeChanged(TestInfo testInfo) throws IOException {
        SharedRedisServer.run("RPUSH %s a b c".formatted(SharedRedisServer.key(testInfo)));
        SharedRedisServer.run("LPOP %s".formatted(SharedRedisServer.key(testInfo)));
        var response = SharedRedisServer.run("LRANGE %s 0 -1".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("*2", "$1", "b", "$1", "c"), response);
    }

    @Test
    void lpopCanTakeHowMuchToRemove(TestInfo testInfo) throws IOException {
        SharedRedisServer.run("RPUSH %s a b c".formatted(SharedRedisServer.key(testInfo)));
        var response = SharedRedisServer.run("LPOP %s 2".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("*2", "$1", "a", "$1", "b"), response);
        response = SharedRedisServer.run("LRANGE %s 0 -1".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("*1", "$1", "c"), response);
    }

    @Test
    void lpopWithCountOfOneShouldReturnArrayNotBulkString(TestInfo testInfo) throws IOException {
        // Redis 6.2: when the count argument is present, the reply is ALWAYS
        // an array, even if a single element is returned (LPOP key 1 → *1…).
        SharedRedisServer.run("RPUSH %s a".formatted(SharedRedisServer.key(testInfo)));
        var response = SharedRedisServer.run("LPOP %s 1".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("*1", "$1", "a"), response);
        response = SharedRedisServer.run("LLEN %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of(":0"), response);
    }

    @Test
    void lpopWithCountOnMissingKeyShouldReturnNilArray(TestInfo testInfo) throws IOException {
        // RESP2: LPOP without count on a missing key → $-1 (null bulk string),
        // LPOP with count on a missing key → *-1 (null array).
        var response = SharedRedisServer.run("LPOP %s 1".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("*-1"), response);
    }

    @Test
    void lpopCanShouldRemoveAllIfGivenValueIsGreaterThanSize(TestInfo testInfo) throws IOException {
        SharedRedisServer.run("RPUSH %s a b c".formatted(SharedRedisServer.key(testInfo)));
        var response = SharedRedisServer.run("LPOP %s 100".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("*3", "$1", "a", "$1", "b", "$1", "c"), response);
        response = SharedRedisServer.run("LLEN %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of(":0"), response);
    }

    @Test
    void lpopWithoutArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.run("LPOP");
        assertEquals(List.of("-ERR wrong number of arguments for 'LPOP' command"), response);
    }

    @Test
    void lpopWithTooManyArgumentsRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("LPOP %s 1 extra".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-ERR wrong number of arguments for 'LPOP' command"), response);
    }

    @Test
    void lpopWithNonNumericCountRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("LPOP %s xyz".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-WRONGTYPE Optional number argument has to be numeric greater or equal than 1"), response);
    }

    @Test
    void lpopAfterKeyExpiredShouldReturnNullBulkString(TestInfo testInfo) throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("SET %s v PX 100".formatted(key));
        TimeUnit.SECONDS.sleep(1);
        var response = SharedRedisServer.run("LPOP %s".formatted(key));
        assertEquals(List.of("$-1"), response);
    }

}
