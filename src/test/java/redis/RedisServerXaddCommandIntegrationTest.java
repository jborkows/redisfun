package redis;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerXaddCommandIntegrationTest {

    @Test
    void createKey(TestInfo testInfo) throws IOException {
        var key = SharedRedisServer.key(testInfo);
        var entryId = "entryId";
        var response = SharedRedisServer.run("XADD stream_key entryId key value key2 value2".replaceAll("stream_key", key).replaceAll("entryId", entryId));
        assertEquals(List.of("$" + entryId.length(), entryId), response);
    }

    @Test
    void noStreamKeyResultsInError() throws IOException {
        var response = SharedRedisServer.run("XADD");
        assertEquals(List.of("-ERR wrong number of arguments for 'XADD' command"), response);
    }

}
