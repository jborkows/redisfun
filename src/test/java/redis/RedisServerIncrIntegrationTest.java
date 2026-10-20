package redis;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerIncrIntegrationTest {

    @Test
    void incrForExistingNumericKeyShouldIncrementByOne(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("SET %s 41".formatted(key));
        var response = SharedRedisServer.run("INCR %s".formatted(key));
        assertEquals(List.of(":42"), response);
    }

    @Test
    void incrMultipleTimesShouldIncrementEachTime(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("SET %s 0".formatted(key));
        assertEquals(List.of(":1"), SharedRedisServer.run("INCR %s".formatted(key)));
        assertEquals(List.of(":2"), SharedRedisServer.run("INCR %s".formatted(key)));
        assertEquals(List.of(":3"), SharedRedisServer.run("INCR %s".formatted(key)));
    }

    @Test
    void incrForNotExistingKeyShouldReturnOne(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("INCR %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of(":1"), response);
    }

    @Test
    void incrForNonNumericValueShouldReturnError(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("SET %s abc".formatted(key));
        var response = SharedRedisServer.run("INCR %s".formatted(key));
        assertEquals(List.of("-ERR value is not an integer or out of range"), response);
    }

    @Test
    void incrWithoutArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.run("INCR");
        assertEquals(List.of("-ERR wrong number of arguments for 'INCR' command"), response);
    }

    @Test
    void incrWithTooManyArgumentsRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("INCR %s extra".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-ERR wrong number of arguments for 'INCR' command"), response);
    }

}
