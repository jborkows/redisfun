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

}
