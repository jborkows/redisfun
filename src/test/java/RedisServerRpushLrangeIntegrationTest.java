import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerRpushLrangeIntegrationTest {

    @Test
    void rpush() throws IOException {
        var response = SharedRedisServer.run("RPUSH list_key \"foo\"");
        assertEquals(List.of(":1"), response);
        response = SharedRedisServer.run("RPUSH list_key \"zoo\"");
        assertEquals(List.of(":2"), response);
    }

    @Test
    void rpushMultiple() throws IOException {
        var response = SharedRedisServer.run("RPUSH list_multiple foo zoo moo");
        assertEquals(List.of(":3"), response);
    }

    @Test
    void lRangeNoElementsEmptyList() throws IOException {
        var response = SharedRedisServer.run("LRANGE lRangeNoElementsEmptyList 0 1");
        assertEquals(List.of("*0"), response);
    }

    @Test
    void lRangeFindRange() throws IOException {
        SharedRedisServer.run("RPUSH lRangeFindRange a b c d");
        var response = SharedRedisServer.run("LRANGE lRangeFindRange 1 2");
        assertEquals(List.of("*2", "$1", "b", "$1", "c"), response);
        response = SharedRedisServer.run("LRANGE lRangeFindRange 0 1");
        assertEquals(List.of("*2", "$1", "a", "$1", "b"), response);
    }

    @Test
    void lRangeStopLargerThanSize() throws IOException {
        SharedRedisServer.run("RPUSH lRangeStopLargerThanSize a b c d");
        var response = SharedRedisServer.run("LRANGE lRangeStopLargerThanSize 1 10");
        assertEquals(List.of("*3", "$1", "b", "$1", "c", "$1", "d"), response);
    }

    @Test
    void lRangeStartLargerThanStop() throws IOException {
        SharedRedisServer.run("RPUSH lRangeStartLargerThanStop a b c d");
        var response = SharedRedisServer.run("LRANGE lRangeStartLargerThanStop 2 1");
        assertEquals(List.of("*0"), response);
    }

    @TestFactory
    Stream<DynamicTest> lRangeNegativeIndexes() throws IOException {
        var key = "lRangeNegativeIndexes";
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
    void lRangeNegativeStartAfterPositiveStop() throws IOException {
        SharedRedisServer.run("RPUSH lRangeNegativeStartAfterPositiveStop a b c d e");
        var response = SharedRedisServer.run("LRANGE lRangeNegativeStartAfterPositiveStop -1 0");
        assertEquals(List.of("*0"), response);
    }

    @Test
    void lRangeNegativeIndexesForNonExistingKey() throws IOException {
        var response = SharedRedisServer.run("LRANGE lRangeNegativeIndexesForNonExistingKey 0 -1");
        assertEquals(List.of("*0"), response);
    }
}
