import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;

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

}
