package redis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliParserTest {

    @Test
    void emptyArgsReturnsNoChangers() {
        var changers = CliParser.parse(new String[]{});

        assertTrue(changers.isEmpty());
    }

    @Test
    void parsesPortOption() {
        var changers = CliParser.parse(new String[]{"--port", "6380"});

        assertEquals(1, changers.size());
        var config = new RedisServer.Config(changers.toArray(new RedisServer.ConfigChanger[0]));
        assertEquals(6380, config.port);
    }

    @Test
    void rejectsUnknownOption() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> CliParser.parse(new String[]{"--unknown"})
        );

        assertEquals("Invalid option: --unknown", exception.getMessage());
    }

    @Test
    void rejectsMissingPortValue() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> CliParser.parse(new String[]{"--port"})
        );

        assertEquals("Missing port number", exception.getMessage());
    }

    @Test
    void rejectsInvalidPortNumber() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> CliParser.parse(new String[]{"--port", "not-a-number"})
        );

        assertEquals("Invalid port number", exception.getMessage());
    }
}
