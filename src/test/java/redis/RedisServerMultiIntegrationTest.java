package redis;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerMultiIntegrationTest {

    @Test
    void multiShouldReturnOk() throws IOException {
        var response = SharedRedisServer.run("MULTI");
        assertEquals(List.of("+OK"), response);
    }

    @Test
    void execWithoutMultiShouldFail() throws IOException {
        var response = SharedRedisServer.run("EXEC");
        assertEquals(List.of("-ERR EXEC without MULTI"), response);
    }

    @Test
    void multiWithArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.run("MULTI extra");
        assertEquals(List.of("-ERR wrong number of arguments for 'MULTI' command"), response);
    }

    @Test
    void emptyTransactionShouldReturnEmptyArrayAndFollowingExecFails() throws IOException {
        var response = SharedRedisServer.runOnSameConnection("MULTI", "EXEC", "EXEC");
        assertEquals(List.of("+OK", "*0", "-ERR EXEC without MULTI"), response);
    }

    @Test
    void multiShouldQueueCommands(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        var response = SharedRedisServer.runOnSameConnection(
                "MULTI",
                "SET %s 1".formatted(key),
                "INCR %s".formatted(key)
        );
        assertEquals(List.of("+OK", "+QUEUED", "+QUEUED"), response);
    }

    @Test
    void execShouldReturnResponsesForQueuedCommands(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        var otherKey = key + ".other";
        var response = SharedRedisServer.runOnSameConnection(
                "MULTI",
                "SET %s 6".formatted(key),
                "INCR %s".formatted(key),
                "INCR %s".formatted(otherKey),
                "GET %s".formatted(otherKey),
                "EXEC"
        );
        assertEquals(List.of(
                "+OK",
                "+QUEUED",
                "+QUEUED",
                "+QUEUED",
                "+QUEUED",
                "*4", "+OK", ":7", ":1", "$1", "1"
        ), response);
    }

    @Test
    void execShouldApplySideEffects(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.runOnSameConnection(
                "MULTI",
                "SET %s 6".formatted(key),
                "INCR %s".formatted(key),
                "EXEC"
        );
        var response = SharedRedisServer.run("GET %s".formatted(key));
        assertEquals(List.of("$1", "7"), response);
    }

}
