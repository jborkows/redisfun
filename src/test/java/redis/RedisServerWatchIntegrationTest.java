package redis;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerWatchIntegrationTest {

    @Test
    void watchWithSingleKeyShouldReturnOk(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("WATCH %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("+OK"), response);
    }

    @Test
    void watchWithMultipleKeysShouldReturnOk(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        var response = SharedRedisServer.run("WATCH %s %s2 %s3".formatted(key, key, key));
        assertEquals(List.of("+OK"), response);
    }

    @Test
    void watchWithoutArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.run("WATCH");
        assertEquals(List.of("-ERR wrong number of arguments for 'WATCH' command"), response);
    }
}
