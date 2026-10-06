import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerTypeCommandIntegrationTest {

    @Test
    void forNotDefinedKeyItShouldReturnNone(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        var response = SharedRedisServer.run("TYPE %s".formatted(key));
        assertEquals(List.of("+none"), response);
    }

    @Test
    void singleStringShouldReturnString(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("SET %s X".formatted(key));
        var response = SharedRedisServer.run("TYPE %s".formatted(key));
        assertEquals(List.of("+string"), response);
    }

    @Test
    void expiredStringShouldAlsoBeNull(TestInfo testInfo) throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("SET %s X PX 100".formatted(key));
        TimeUnit.SECONDS.sleep(1);
        var response = SharedRedisServer.run("TYPE %s".formatted(key));
        assertEquals(List.of("+none"), response);
    }

    @Test
    void listStringShouldReturnList(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        SharedRedisServer.run("LPUSH %s X".formatted(key));
        var response = SharedRedisServer.run("TYPE %s".formatted(key));
        assertEquals(List.of("+list"), response);
    }

}
