package redis.commands;

import org.junit.jupiter.api.Test;
import redis.ClientId;
import redis.protocol.Command;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterpreterTest {

    private final Interpreter interpreter = new Interpreter(ClientId.generate());

    @Test
    void respondsToPing() {
        var effect = interpreter.apply(new Command("PING", List.of()));
        assertEquals(new Effect.SimpleString("PONG"), effect);
    }

    @Test
    void echoesBackTheArgument() {
        var effect = interpreter.apply(new Command("ECHO", List.of("hey")));
        assertEquals(new Effect.BulkString("hey"), effect);
    }

    @Test
    void echoesValueStartingWithStar() {
        var effect = interpreter.apply(new Command("ECHO", List.of("*foo")));
        assertEquals(new Effect.BulkString("*foo"), effect);
    }

    @Test
    void echoesValueStartingWithDollar() {
        var effect = interpreter.apply(new Command("ECHO", List.of("$XXXX")));
        assertEquals(new Effect.BulkString("$XXXX"), effect);
    }

    @Test
    void respondsWithErrorForEchoWithoutArguments() {
        var effect = interpreter.apply(new Command("ECHO", List.of()));
        assertEquals(new Effect.Error("wrong number of arguments for 'echo' command"), effect);
    }

    @Test
    void respondsWithErrorForEchoWithTooManyArguments() {
        var effect = interpreter.apply(new Command("ECHO", List.of("a", "b")));
        assertEquals(new Effect.Error("wrong number of arguments for 'echo' command"), effect);
    }

    @Test
    void respondsWithErrorForGetWithoutArguments() {
        var effect = interpreter.apply(new Command("GET", List.of()));
        assertEquals(new Effect.Error("wrong number of arguments for 'get' command"), effect);
    }

    @Test
    void respondsWithErrorForGetWithTooManyArguments() {
        var effect = interpreter.apply(new Command("GET", List.of("a", "b")));
        assertEquals(new Effect.Error("wrong number of arguments for 'get' command"), effect);
    }

    @Test
    void respondsWithErrorForSetWithMissingValue() {
        var effect = interpreter.apply(new Command("SET", List.of("a")));
        assertEquals(new Effect.Error("wrong number of arguments for '" + SetCommand.NAME + "' command"), effect);
    }

    @Test
    void acceptsSetWithExpiryOption() {
        var effect = interpreter.apply(new Command("SET", List.of("a", "b", "PX", "5000")));
        assertEquals(new Effect.SimpleString("OK"), effect);
    }

    @Test
    void respondsWithSyntaxErrorForSetExpiryWithoutTime() {
        var effect = interpreter.apply(new Command("SET", List.of("a", "b", "PX")));
        assertEquals(new Effect.Error("syntax error"), effect);
    }

    @Test
    void respondsWithSyntaxErrorForSetWithBothExAndPx() {
        var effect = interpreter.apply(new Command("SET", List.of("a", "b", "EX", "5", "PX", "5000")));
        assertEquals(new Effect.Error("syntax error"), effect);
    }

    @Test
    void encodesNestedArrays() {
        var effect = Messages.array(
                Messages.array(new Effect.SimpleString("a")),
                new Effect.SimpleString("b")
        );
        assertEquals(new Effect.Array(List.of(
                new Effect.Array(List.of(new Effect.SimpleString("a"))),
                new Effect.SimpleString("b")
        )), effect);
        assertEquals("*2\r\n*1\r\n+a\r\n+b\r\n", effect.text());
    }
}
