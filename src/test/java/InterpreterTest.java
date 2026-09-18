import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterTest {

    @Test
    void respondsToPing() {
        var effects = Interpreter.interpret(new Command("PING", List.of()));
        assertEquals(List.of(new PrintText("+PONG")), effects);
    }

    @Test
    void echoesBackTheArgument() {
        var effects = Interpreter.interpret(new Command("ECHO", List.of("hey")));
        assertEquals(List.of(new PrintText("$3"), new PrintText("hey")), effects);
    }

    @Test
    void echoesValueStartingWithStar() {
        var effects = Interpreter.interpret(new Command("ECHO", List.of("*foo")));
        assertEquals(List.of(new PrintText("$4"), new PrintText("*foo")), effects);
    }

    @Test
    void echoesValueStartingWithDollar() {
        var effects = Interpreter.interpret(new Command("ECHO", List.of("$XXXX")));
        assertEquals(List.of(new PrintText("$5"), new PrintText("$XXXX")), effects);
    }

    @Test
    void respondsWithErrorForEchoWithoutArguments() {
        var effects = Interpreter.interpret(new Command("ECHO", List.of()));
        assertEquals(List.of(new PrintText("-ERR wrong number of arguments for 'echo' command")), effects);
    }

    @Test
    void respondsWithErrorForEchoWithTooManyArguments() {
        var effects = Interpreter.interpret(new Command("ECHO", List.of("a", "b")));
        assertEquals(List.of(new PrintText("-ERR wrong number of arguments for 'echo' command")), effects);
    }
}
