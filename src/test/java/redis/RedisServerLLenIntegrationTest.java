package redis;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerLLenIntegrationTest {

    @Test
    void llenForNotExistingListShouldReturnZero(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("LLEN %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of(":0"), response);
    }

    @Test
    void llenForExistingShouldReturnExisting(TestInfo testInfo) throws IOException {
        SharedRedisServer.run("RPUSH %s a b c".formatted(SharedRedisServer.key(testInfo)));
        var response = SharedRedisServer.run("LLEN %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of(":3"), response);
    }

    @Test
    void llenWithoutArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.run("LLEN");
        assertEquals(List.of("-ERR wrong number of arguments for 'LLEN' command"), response);
    }

    @Test
    void llenWithTooManyArgumentsRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("LLEN %s extra".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-ERR wrong number of arguments for 'LLEN' command"), response);
    }

}
