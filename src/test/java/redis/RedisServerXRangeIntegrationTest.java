package redis;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerXRangeIntegrationTest {

    @Test
    void xrangeReturnsEntriesInRange(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("XADD %s 1526985054069-0 temperature 36 humidity 95".formatted(key));
        SharedRedisServer.run("XADD %s 1526985054079-0 temperature 37 humidity 94".formatted(key));

        var response = SharedRedisServer.run("XRANGE %s 1526985054069-0 1526985054079-0".formatted(key));

        assertEquals(List.of(
                "*2",
                "*2", "$15", "1526985054069-0",
                "*4", "$11", "temperature", "$2", "36", "$8", "humidity", "$2", "95",
                "*2", "$15", "1526985054079-0",
                "*4", "$11", "temperature", "$2", "37", "$8", "humidity", "$2", "94"
        ), response);
    }

    @Test
    void xrangeReturnsEntriesWithSimpleSequentialIds(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("XADD %s 0-1 foo bar".formatted(key));
        SharedRedisServer.run("XADD %s 0-2 bar baz".formatted(key));
        SharedRedisServer.run("XADD %s 0-3 baz foo".formatted(key));

        var response = SharedRedisServer.run("XRANGE %s 0-2 0-3".formatted(key));

        assertEquals(List.of(
                "*2",
                "*2", "$3", "0-2",
                "*2", "$3", "bar", "$3", "baz",
                "*2", "$3", "0-3",
                "*2", "$3", "baz", "$3", "foo"
        ), response);
    }

    @Test
    void xrangeReturnsEntriesWithSimpleSequentialIdsForMinus(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("XADD %s 0-1 foo bar".formatted(key));
        SharedRedisServer.run("XADD %s 0-2 bar baz".formatted(key));
        SharedRedisServer.run("XADD %s 0-3 baz foo".formatted(key));

        var response = SharedRedisServer.run("XRANGE %s - 0-2".formatted(key));

        assertEquals(List.of(
                "*2",
                "*2", "$3", "0-1",
                "*2", "$3", "foo", "$3", "bar",
                "*2", "$3", "0-2",
                "*2", "$3", "bar", "$3", "baz"
        ), response);
    }

    @Test
    void xrangeWithoutArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.run("XRANGE");
        assertEquals(List.of("-ERR wrong number of arguments for 'XRANGE' command"), response);
    }

    @Test
    void xrangeWithTooFewArgumentsRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("XRANGE %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-ERR wrong number of arguments for 'XRANGE' command"), response);
    }

    @Test
    void xrangeWithTooManyArgumentsRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("XRANGE %s 1-1 2-2 extra".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-ERR wrong number of arguments for 'XRANGE' command"), response);
    }
}
