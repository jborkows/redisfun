package redis;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerXReadIntegrationTest {

    @Test
    void xreadReturnsEntriesAfterGivenId(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("XADD %s 0-1 temperature 96".formatted(key));

        var response = SharedRedisServer.run("XREAD STREAMS %s 0-0".formatted(key));

        assertEquals(List.of(
                "*1",
                "*2", "$" + key.length(), key,
                "*1",
                "*2", "$3", "0-1",
                "*2", "$11", "temperature", "$2", "96"
        ), response);
    }

    @Test
    void xreadWithoutArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.run("XREAD");
        assertEquals(List.of("-ERR wrong number of arguments for 'XREAD' command"), response);
    }

    @Test
    void xreadWithTooFewArgumentsRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("XREAD STREAMS %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-ERR wrong number of arguments for 'XREAD' command"), response);
    }

    @Test
    void xreadWithTooManyArgumentsRespondsWithError(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        var response = SharedRedisServer.run("XREAD STREAMS %s 0-0 extra".formatted(key));
        assertEquals(List.of("-ERR wrong number of arguments for 'XREAD' command"), response);
    }
}
