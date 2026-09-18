import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    @Test
    void singleCommand() {
        var commands = parse("*1\r\n$2\r\nHA\r\n");
        assertEquals(1, commands.size());
        assertEquals("HA", commands.getFirst().name());
        assertTrue(commands.getFirst().args().isEmpty());
    }

    @Test
    void commandWithOneArgument() {
        var commands = parse("*2\r\n$1\r\nA\r\n$2\r\nBB\r\n");
        assertEquals(1, commands.size());
        assertEquals("A", commands.getFirst().name());
        assertEquals(List.of("BB"), commands.getFirst().args());
    }

    @Test
    void doesNotEmitCommandForIncompleteInput() {
        assertTrue(parse("*1").isEmpty());
        assertTrue(parse("*1\r\n$2\r\nHA").isEmpty());
    }

    @Test
    void parsesMultipleCommandsFromOneStream() {
        var commands = parse("*1\r\n$2\r\nHA\r\n*2\r\n$1\r\nA\r\n$2\r\nBB\r\n");
        assertEquals(2, commands.size());
        assertEquals("HA", commands.get(0).name());
        assertEquals("A", commands.get(1).name());
        assertEquals(List.of("BB"), commands.get(1).args());
    }

    @Test
    void rejectsElementWithoutDollar() {
        var result = parseAll("*1\r\n2\r\n");
        assertTrue(result.commands().isEmpty());
        assertEquals(1, result.invalid().size());
    }

    @Test
    void rejectsArrayOfZero() {
        var result = parseAll("*0\r\n");
        assertTrue(result.commands().isEmpty());
        assertEquals(1, result.invalid().size());
    }

    @Test
    void rejectsMismatchedBulkLength() {
        var result = parseAll("*1\r\n$1\r\nHA");
        assertTrue(result.commands().isEmpty());
        assertEquals(1, result.invalid().size());
    }

    @Test
    void bulkLongerThanContentIsTreatedAsIncomplete() {
        var result = parseAll("*1\r\n$5\r\nHA\r\n");
        assertTrue(result.commands().isEmpty());
        assertTrue(result.invalid().isEmpty());
    }

    @Test
    void propagatesInvalidTokenAsInvalidCommand() {
        var result = parseAll("*1\r\n$2\r\nHA\n");
        assertTrue(result.commands().isEmpty());
        assertEquals(1, result.invalid().size());
    }

    @Test
    void recoversAfterInvalidCommand() {
        var result = parseAll("*1\r\n2\r\n*1\r\n$2\r\nHA\r\n");
        assertEquals(1, result.invalid().size());
        assertEquals(1, result.commands().size());
        assertEquals("HA", result.commands().getFirst().name());
    }

    @Test
    void acceptsEchoOfValueStartingWithStar() {
        var commands = parse("*2\r\n$4\r\nECHO\r\n$4\r\n*foo\r\n");
        assertEquals(1, commands.size());
        assertEquals("ECHO", commands.getFirst().name());
        assertEquals(List.of("*foo"), commands.getFirst().args());
    }

    @Test
    void acceptsEchoOfValueStartingWithDollar() {
        var commands = parse("*2\r\n$4\r\nECHO\r\n$5\r\n$XXXX\r\n");
        assertEquals(1, commands.size());
        assertEquals("ECHO", commands.getFirst().name());
        assertEquals(List.of("$XXXX"), commands.getFirst().args());
    }

    private List<Command> parse(String input) {
        var result = parseAll(input);
        assertTrue(result.invalid().isEmpty(), () -> "Unexpected invalid commands: " + result.invalid());
        return result.commands();
    }

    private ParseResult parseAll(String input) {
        var lexer = new Lexer();
        var parser = new Parser();
        var commands = new ArrayList<Command>();
        var invalid = new ArrayList<CommandState.InvalidCommand>();
        for (byte b : input.getBytes(StandardCharsets.US_ASCII)) {
            var lexed = lexer.onByte(b);
            lexer = lexed.lexer();
            if (lexed.state() instanceof TokenState.Ready(Token token)) {
                var parsed = parser.on(token);
                parser = parsed.parser();
                if (parsed.state() instanceof CommandState.Ready(Command command)) {
                    commands.add(command);
                } else if (parsed.state() instanceof CommandState.InvalidCommand invalidCommand) {
                    invalid.add(invalidCommand);
                }
            }
        }
        return new ParseResult(commands, invalid);
    }

    private record ParseResult(List<Command> commands, List<CommandState.InvalidCommand> invalid) {
    }
}
