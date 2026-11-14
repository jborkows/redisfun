package redis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisServerInfoIntegrationTest {

    @Test
    void infoReplicationShouldReturnRoleReplicationIdAndOffset() {
        SharedRedisServer.runOnSeparateMaster(() -> {
            var response = SharedRedisServer.run("INFO replication");
            assertEquals(4, response.size(),
                    "INFO replication must be a single bulk string with three payload lines, got %s".formatted(response));

            var role = response.get(1);
            assertEquals("role:master", role);

            var replicationIdLine = response.get(2);
            assertTrue(replicationIdLine.startsWith("master_replid:"),
                    "Second payload line should carry master_replid, got %s".formatted(response));
            var replicationId = replicationIdLine.substring("master_replid:".length());
            assertEquals(40, replicationId.length(),
                    "master_replid must be 40 characters, got '%s'".formatted(replicationId));
            assertTrue(replicationId.chars().allMatch(Character::isLetterOrDigit),
                    "master_replid must be alphanumeric, got '%s'".formatted(replicationId));

            var offsetLine = response.get(3);
            assertEquals("master_repl_offset:0", offsetLine);

            var payloadLength = role.length() + 2 + replicationIdLine.length() + 2 + offsetLine.length();
            assertEquals("$" + payloadLength, response.getFirst());
        });
    }

    @Test
    void infoWithoutArgumentsShouldReturnForSlave() {
        var response = SharedRedisServer.runForSingleSlave(() -> SharedRedisServer.run("INFO"));
        assertEquals(List.of("$10", "role:slave"), response);
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
