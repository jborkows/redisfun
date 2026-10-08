package redis.commands;

import redis.protocol.Command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterTest {

    @Test
    void respondsToPing() {
        var effects = Interpreter.interpret(new Command("PING", List.of()));
        assertEquals(List.of(new Effect.SimpleString("PONG")), effects);
    }

    @Test
    void echoesBackTheArgument() {
        var effects = Interpreter.interpret(new Command("ECHO", List.of("hey")));
        assertEquals(List.of(new Effect.BulkString("hey")), effects);
    }

    @Test
    void echoesValueStartingWithStar() {
        var effects = Interpreter.interpret(new Command("ECHO", List.of("*foo")));
        assertEquals(List.of(new Effect.BulkString("*foo")), effects);
    }

    @Test
    void echoesValueStartingWithDollar() {
        var effects = Interpreter.interpret(new Command("ECHO", List.of("$XXXX")));
        assertEquals(List.of(new Effect.BulkString("$XXXX")), effects);
    }

    @Test
    void respondsWithErrorForEchoWithoutArguments() {
        var effects = Interpreter.interpret(new Command("ECHO", List.of()));
        assertEquals(List.of(new Effect.Error("wrong number of arguments for 'echo' command")), effects);
    }

    @Test
    void respondsWithErrorForEchoWithTooManyArguments() {
        var effects = Interpreter.interpret(new Command("ECHO", List.of("a", "b")));
        assertEquals(List.of(new Effect.Error("wrong number of arguments for 'echo' command")), effects);
    }

    @Test
    void respondsWithErrorForGetWithoutArguments() {
        var effects = Interpreter.interpret(new Command("GET", List.of()));
        assertEquals(List.of(new Effect.Error("wrong number of arguments for 'get' command")), effects);
    }

    @Test
    void respondsWithErrorForGetWithTooManyArguments() {
        var effects = Interpreter.interpret(new Command("GET", List.of("a", "b")));
        assertEquals(List.of(new Effect.Error("wrong number of arguments for 'get' command")), effects);
    }

    @Test
    void respondsWithErrorForSetWithMissingValue() {
        var effects = Interpreter.interpret(new Command("SET", List.of("a")));
        assertEquals(List.of(new Effect.Error("wrong number of arguments for '" + SetCommand.NAME + "' command")), effects);
    }

    @Test
    void acceptsSetWithExpiryOption() {
        var effects = Interpreter.interpret(new Command("SET", List.of("a", "b", "PX", "5000")));
        assertEquals(List.of(new Effect.SimpleString("OK")), effects);
    }

    @Test
    void respondsWithSyntaxErrorForSetExpiryWithoutTime() {
        var effects = Interpreter.interpret(new Command("SET", List.of("a", "b", "PX")));
        assertEquals(List.of(new Effect.Error("syntax error")), effects);
    }

    @Test
    void respondsWithSyntaxErrorForSetWithBothExAndPx() {
        var effects = Interpreter.interpret(new Command("SET", List.of("a", "b", "EX", "5", "PX", "5000")));
        assertEquals(List.of(new Effect.Error("syntax error")), effects);
    }

    @Test
    void encodesNestedArrays() {
        var effects = Messages.array(
                Messages.array(new Effect.SimpleString("a")).getFirst(),
                new Effect.SimpleString("b")
        );
        assertEquals(List.of(new Effect.Array(List.of(
                new Effect.Array(List.of(new Effect.SimpleString("a"))),
                new Effect.SimpleString("b")
        ))), effects);
        assertEquals("*2\r\n*1\r\n+a\r\n+b\r\n", effects.getFirst().text());
    }
}
