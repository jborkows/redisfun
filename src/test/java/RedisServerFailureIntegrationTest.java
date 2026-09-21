import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Failure scenarios: commands that are rejected with an explicit error reply,
 * and malformed input that the server silently ignores (no response
 * at all, observed as an empty reply after the read timeout).
 */
@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerFailureIntegrationTest {

    private static final List<String> WRONG_ECHO_ARITY =
            List.of("-ERR wrong number of arguments for 'echo' command");

    @Test
    void echoWithoutArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.sendCommand("*1", "$4", "ECHO");
        assertEquals(WRONG_ECHO_ARITY, response);
    }

    @Test
    void echoWithTooManyArgumentsRespondsWithError() throws IOException {
        var response = SharedRedisServer.sendCommand("*3", "$4", "ECHO", "$3", "hey", "$3", "you");
        assertEquals(WRONG_ECHO_ARITY, response);
    }

    @Test
    void unknownCommandRespondsWithError() throws IOException {
        var response = SharedRedisServer.sendCommand("*1", "$3", "FOO");
        assertEquals(List.of("-ERR Unknown command 'FOO'"), response);
    }

    @Test
    void unframedCommandReceivesNoResponse() throws IOException {
        // PING without RESP array framing is not a valid command
        var response = SharedRedisServer.sendCommand("PING");
        assertEquals(List.of(), response);
    }

    @Test
    void emptyArrayReceivesNoResponse() throws IOException {
        var response = SharedRedisServer.sendCommand("*0");
        assertEquals(List.of(), response);
    }

    @Test
    void nonNumericArrayLengthReceivesNoResponse() throws IOException {
        var response = SharedRedisServer.sendCommand("*x", "$4", "PING");
        assertEquals(List.of(), response);
    }

    @Test
    void bulkStringLongerThanContentReceivesNoResponse() throws IOException {
        // Declared length 5 but only 4 bytes of content follow
        var response = SharedRedisServer.sendCommand("*1", "$5", "PING");
        assertEquals(List.of(), response);
    }
}
