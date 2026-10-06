import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerSetGetIntegrationTest {

    @Test
    void getForNotExistingKey(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("GET %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("$-1"), response);
    }

    @Test
    void setKeyAndFetch(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        var response = SharedRedisServer.run("SET %s X".formatted(key));
        assertEquals(List.of("+OK"), response);
        response = SharedRedisServer.run("GET %s".formatted(key));
        assertEquals(List.of("$1", "X"), response);
    }

    @Test
    void setKeyAndFetchWithExpire(TestInfo testInfo) throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var response = SharedRedisServer.run("SET %s X EX 2".formatted(key));
        assertEquals(List.of("+OK"), response);
        response = SharedRedisServer.run("GET %s".formatted(key));
        assertEquals(List.of("$1", "X"), response);
        TimeUnit.SECONDS.sleep(2);
        response = SharedRedisServer.run("GET %s".formatted(key));
        assertEquals(List.of("$-1"), response);
    }

    @Test
    void setKeyAndFetchWithExpireMiliseconds(TestInfo testInfo) throws IOException, InterruptedException {
        var key = SharedRedisServer.key(testInfo);
        var response = SharedRedisServer.run("SET %s Z PX 2000".formatted(key));
        assertEquals(List.of("+OK"), response);
        response = SharedRedisServer.run("GET %s".formatted(key));
        assertEquals(List.of("$1", "Z"), response);
        TimeUnit.SECONDS.sleep(2);
        response = SharedRedisServer.run("GET %s".formatted(key));
        assertEquals(List.of("$-1"), response);
    }

    @Test
    void getWithoutArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.run("GET");
        assertEquals(List.of("-ERR wrong number of arguments for 'get' command"), response);
    }

    @Test
    void getWithTooManyArgumentsRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("GET %s extra".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-ERR wrong number of arguments for 'get' command"), response);
    }

    @Test
    void setWithoutArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.run("SET");
        assertEquals(List.of("-ERR wrong number of arguments for 'SET' command"), response);
    }

    @Test
    void setWithMissingValueRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("SET %s".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-ERR wrong number of arguments for 'SET' command"), response);
    }

    @Test
    void setWithNonNumericExpiryRespondsWithError(TestInfo testInfo) throws IOException {
        var response = SharedRedisServer.run("SET %s X EX abc".formatted(SharedRedisServer.key(testInfo)));
        assertEquals(List.of("-ERR syntax error"), response);
    }
}
