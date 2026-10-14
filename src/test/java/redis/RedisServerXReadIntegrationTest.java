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
    void xreadUsesStrictlyGreaterThanBoundary(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("XADD %s 0-1 temperature 96".formatted(key));
        SharedRedisServer.run("XADD %s 0-2 temperature 97".formatted(key));
        SharedRedisServer.run("XADD %s 0-3 temperature 98".formatted(key));

        var response = SharedRedisServer.run("XREAD STREAMS %s 0-1".formatted(key));

        assertEquals(List.of(
                "*1",
                "*2", "$" + key.length(), key,
                "*2",
                "*2", "$3", "0-2",
                "*2", "$11", "temperature", "$2", "97",
                "*2", "$3", "0-3",
                "*2", "$11", "temperature", "$2", "98"
        ), response);
    }

    @Test
    void xreadHandlesMultipleStreamsWithDifferentStartIds(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        var firstStream = key + "-1";
        var secondStream = key + "-2";
        SharedRedisServer.run("XADD %s 0-1 temperature 95".formatted(firstStream));
        SharedRedisServer.run("XADD %s 0-2 humidity 97".formatted(secondStream));

        var response = SharedRedisServer.run("XREAD STREAMS %s %s 0-0 0-1".formatted(firstStream, secondStream));

        assertEquals(List.of(
                "*2",
                "*2", "$" + firstStream.length(), firstStream,
                "*1",
                "*2", "$3", "0-1",
                "*2", "$11", "temperature", "$2", "95",
                "*2", "$" + secondStream.length(), secondStream,
                "*1",
                "*2", "$3", "0-2",
                "*2", "$8", "humidity", "$2", "97"
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

    @Test
    void xreadWithInvalidIdRespondsWithWrongTypeError(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("XADD %s 0-1 temperature 96".formatted(key));

        var response = SharedRedisServer.run("XREAD STREAMS %s badid".formatted(key));

        assertEquals(List.of("-WRONGTYPE Used wrong ids"), response);
    }

    @Test
    void xreadAgainstNonStreamKeyRespondsWithWrongTypeError(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("SET %s value".formatted(key));

        var response = SharedRedisServer.run("XREAD STREAMS %s 0-0".formatted(key));

        assertEquals(List.of("-WRONGTYPE Operation XREAD allowed only for streams"), response);
    }
}
