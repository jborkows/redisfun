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

}
