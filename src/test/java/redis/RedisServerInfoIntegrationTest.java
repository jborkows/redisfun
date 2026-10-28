package redis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisServerInfoIntegrationTest {

    @Test
    void infoReplicationShouldReturnMasterRole() throws IOException {
        var response = SharedRedisServer.run("INFO replication");
        assertEquals(List.of("$11", "role:master"), response);
    }

    @Test
    void infoWithoutArgumentsShouldReturnMasterRole() throws IOException {
        var response = SharedRedisServer.run("INFO");
        assertEquals(List.of("$11", "role:master"), response);
    }

    @Test
    void infoWithTooManyArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.run("INFO replication extra");
        assertEquals(List.of("-ERR wrong number of arguments for 'INFO' command"), response);
    }

    @Test
    void infoWithUnknownSectionReturnsEmptyBulkString() throws IOException {
        var response = SharedRedisServer.run("INFO server");
        assertEquals(List.of("$0", ""), response);
    }
}
