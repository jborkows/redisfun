import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerLpushLrangeIntegrationTest {

    @Test
    void lPushWhenNotExists() throws IOException {
        var lPushWhenNotExists = "lPushWhenNotExists";
        var response = SharedRedisServer.run("LPUSH %s a b c".formatted(lPushWhenNotExists));
        assertEquals(List.of(":3"), response);
    }

    @Test
    void lpushInsertsInReverseOrder() throws IOException {
        var key = "lpushInsertsInReverseOrder";
        var response = SharedRedisServer.run("LPUSH %s a b c".formatted(key));
        assertEquals(List.of(":3"), response);
        response = SharedRedisServer.run("LRANGE %s 0 3".formatted(key));
        assertEquals(List.of("*3", "$1", "c", "$1", "b", "$1", "a"), response);
    }

    @Test
    void lpushInSeriesShouldGiveSameResult() throws IOException {
        var key = "lpushInSeriesShouldGiveSameResult";
        SharedRedisServer.run("LPUSH %s a".formatted(key));
        SharedRedisServer.run("LPUSH %s b".formatted(key));
        SharedRedisServer.run("LPUSH %s c".formatted(key));
        var response = SharedRedisServer.run("LRANGE %s 0 3".formatted(key));
        assertEquals(List.of("*3", "$1", "c", "$1", "b", "$1", "a"), response);
    }

    @Test
    void lPushShouldExtendExisting() throws IOException {
        var key = "lPushShouldExtendExisting";
        SharedRedisServer.run("RPUSH %s a b".formatted(key));
        SharedRedisServer.run("LPUSH %s c".formatted(key));
        var response = SharedRedisServer.run("LRANGE %s 0 3".formatted(key));
        assertEquals(List.of("*3", "$1", "c", "$1", "a", "$1", "b"), response);
    }

}
