package redis;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerRpushLrangeIntegrationTest {

    @Test
    void rpush(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        var response = SharedRedisServer.run("RPUSH %s \"foo\"".formatted(key));
        assertEquals(List.of(":1"), response);
        response = SharedRedisServer.run("RPUSH %s \"zoo\"".formatted(key));
        assertEquals(List.of(":2"), response);
    }

    @Test
    void rpushMultiple(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("RPUSH %s foo zoo moo".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of(":3"), response);
    }

    @Test
    void lRangeNoElementsEmptyList(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("LRANGE %s 0 1".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("*0"), response);
    }

    @Test
    void lRangeFindRange(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("RPUSH %s a b c d".formatted(key));
        var response = SharedRedisServer.run("LRANGE %s 1 2".formatted(key));
        assertEquals(List.of("*2", "$1", "b", "$1", "c"), response);
        response = SharedRedisServer.run("LRANGE %s 0 1".formatted(key));
        assertEquals(List.of("*2", "$1", "a", "$1", "b"), response);
    }

    @Test
    void lRangeStopLargerThanSize(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("RPUSH %s a b c d".formatted(key));
        var response = SharedRedisServer.run("LRANGE %s 1 10".formatted(key));
        assertEquals(List.of("*3", "$1", "b", "$1", "c", "$1", "d"), response);
    }

    @Test
    void lRangeStartLargerThanStop(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("RPUSH %s a b c d".formatted(key));
        var response = SharedRedisServer.run("LRANGE %s 2 1".formatted(key));
        assertEquals(List.of("*0"), response);
    }

    @TestFactory
    Stream<DynamicTest> lRangeNegativeIndexes(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("RPUSH %s a b c d e".formatted(key));
        var prefix = "Array a b c d e";
        return Stream.of(
                dynamicTest(prefix + " - last two should be [d, e]", () -> {
                    var response = SharedRedisServer.run("LRANGE %s -2 -1".formatted(key));
                    assertEquals(List.of("*2", "$1", "d", "$1", "e"), response);
                }),
                dynamicTest(prefix + " - from 0 to 3rd from end should be [a, b, c]", () -> {
                    var response = SharedRedisServer.run("LRANGE %s 0 -3".formatted(key));
                    assertEquals(List.of("*3", "$1", "a", "$1", "b", "$1", "c"), response);
                }),
                dynamicTest(prefix + " - start -6 (larger than size, treated as 0) to -3 should be [a, b, c]", () -> {
                    var response = SharedRedisServer.run("LRANGE %s -6 -3".formatted(key));
                    assertEquals(List.of("*3", "$1", "a", "$1", "b", "$1", "c"), response);
                }),
                dynamicTest(prefix + " - from 0 to very negative stop should be [a]", () -> {
                    var response = SharedRedisServer.run("LRANGE %s 0 -100".formatted(key));
                    assertEquals(List.of("*1", "$1", "a"), response);
                })
        );
    }

    @Test
    void lRangeNegativeStartAfterPositiveStop(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("RPUSH %s a b c d e".formatted(key));
        var response = SharedRedisServer.run("LRANGE %s -1 0".formatted(key));
        assertEquals(List.of("*0"), response);
    }

    @Test
    void lRangeNegativeIndexesForNonExistingKey(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("LRANGE %s 0 -1".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("*0"), response);
    }

    @Test
    void rpushWithoutValuesRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("RPUSH %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-ERR wrong number of arguments for 'RPUSH' command"), response);
    }

    @Test
    void lRangeWithoutStartAndStopRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("LRANGE %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-ERR wrong number of arguments for 'LRANGE' command"), response);
    }

    @Test
    void lRangeWithoutStopRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("LRANGE %s 0".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-ERR wrong number of arguments for 'LRANGE' command"), response);
    }

    @Test
    void lRangeWithNonNumericRangeRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("LRANGE %s a b".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-WRONGTYPE Both start and end have to be numbers"), response);
    }
}
