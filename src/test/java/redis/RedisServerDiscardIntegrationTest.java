package redis;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerDiscardIntegrationTest {

    @Test
    void discardShouldReturnOkAndAbortTransaction(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        var response = SharedRedisServer.runOnSameConnection(
                "MULTI",
                "SET %s 41".formatted(key),
                "INCR %s".formatted(key),
                "DISCARD",
                "GET %s".formatted(key),
                "DISCARD"
        );
        assertEquals(List.of(
                "+OK",
                "+QUEUED",
                "+QUEUED",
                "+OK",
                "$-1",
                "-ERR DISCARD without MULTI"
        ), response);
    }

    @Test
    void discardInEmptyTransactionShouldReturnOk() throws IOException {
        var response = SharedRedisServer.runOnSameConnection("MULTI", "DISCARD");
        assertEquals(List.of("+OK", "+OK"), response);
    }

    @Test
    void discardWithoutMultiShouldFail() throws IOException {
        var response = SharedRedisServer.run("DISCARD");
        assertEquals(List.of("-ERR DISCARD without MULTI"), response);
    }

    @Test
    void discardWithArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.run("DISCARD extra");
        assertEquals(List.of("-ERR wrong number of arguments for 'DISCARD' command"), response);
    }
}
