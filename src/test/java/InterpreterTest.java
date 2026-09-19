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

    @Test
    void respondsWithErrorForGetWithoutArguments() {
        var effects = Interpreter.interpret(new Command("GET", List.of()));
        assertEquals(List.of(new PrintText("-ERR wrong number of arguments for 'get' command")), effects);
    }

    @Test
    void respondsWithErrorForGetWithTooManyArguments() {
        var effects = Interpreter.interpret(new Command("GET", List.of("a", "b")));
        assertEquals(List.of(new PrintText("-ERR wrong number of arguments for 'get' command")), effects);
    }

    @Test
    void respondsWithErrorForSetWithMissingValue() {
        var effects = Interpreter.interpret(new Command("SET", List.of("a")));
        assertEquals(List.of(new PrintText("-ERR wrong number of arguments for 'set' command")), effects);
    }

    @Test
    void acceptsSetWithExpiryOption() {
        var effects = Interpreter.interpret(new Command("SET", List.of("a", "b", "PX", "5000")));
        assertEquals(List.of(new PrintText("+OK")), effects);
    }

    @Test
    void respondsWithSyntaxErrorForSetExpiryWithoutTime() {
        var effects = Interpreter.interpret(new Command("SET", List.of("a", "b", "PX")));
        assertEquals(List.of(new PrintText("-ERR syntax error")), effects);
    }

    @Test
    void respondsWithSyntaxErrorForSetWithBothExAndPx() {
        var effects = Interpreter.interpret(new Command("SET", List.of("a", "b", "EX", "5", "PX", "5000")));
        assertEquals(List.of(new PrintText("-ERR syntax error")), effects);
    }
}
