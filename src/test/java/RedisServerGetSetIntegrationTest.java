import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerGetSetIntegrationTest {

    @Test
    void getForNotExistingKey() throws IOException {
        var response = SharedRedisServer.run("GET A");
        assertEquals(List.of("$-1"), response);
    }

    @Test
    void setKeyAndFetch() throws IOException {
        var response = SharedRedisServer.run("SET B X");
        assertEquals(List.of("+OK"), response);
        response = SharedRedisServer.run("GET B");
        assertEquals(List.of("$1", "X"), response);
    }

    @Test
    void setKeyAndFetchWithExpire() throws IOException, InterruptedException {
        var response = SharedRedisServer.run("SET C X EX 2");
        assertEquals(List.of("+OK"), response);
        response = SharedRedisServer.run("GET C");
        assertEquals(List.of("$1", "X"), response);
        TimeUnit.SECONDS.sleep(2);
        response = SharedRedisServer.run("GET C");
        assertEquals(List.of("$-1"), response);
    }

    @Test
    void setKeyAndFetchWithExpireMiliseconds() throws IOException, InterruptedException {
        var response = SharedRedisServer.run("SET D Z PX 2000");
        assertEquals(List.of("+OK"), response);
        response = SharedRedisServer.run("GET D");
        assertEquals(List.of("$1", "Z"), response);
        TimeUnit.SECONDS.sleep(2);
        response = SharedRedisServer.run("GET D");
        assertEquals(List.of("$-1"), response);
    }

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
}
