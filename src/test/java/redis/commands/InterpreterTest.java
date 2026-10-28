package redis.commands;

import org.junit.jupiter.api.Test;
import redis.protocol.Command;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterpreterTest {

    private final Interpreter interpreter = new Interpreter();

    private Effect apply(Command command) {
        var effect = new AtomicReference<Effect>();
        var latch = new CountDownLatch(1);
        interpreter.apply(command, e -> {
            effect.set(e);
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return effect.get();
    }

    @Test
    void respondsToPing() {
        var effect = apply(new Command("PING", List.of()));
        assertEquals(new Effect.SimpleString("PONG"), effect);
    }

    @Test
    void echoesBackTheArgument() {
        var effect = apply(new Command("ECHO", List.of("hey")));
        assertEquals(new Effect.BulkString("hey"), effect);
    }

    @Test
    void echoesValueStartingWithStar() {
        var effect = apply(new Command("ECHO", List.of("*foo")));
        assertEquals(new Effect.BulkString("*foo"), effect);
    }

    @Test
    void echoesValueStartingWithDollar() {
        var effect = apply(new Command("ECHO", List.of("$XXXX")));
        assertEquals(new Effect.BulkString("$XXXX"), effect);
    }

    @Test
    void respondsWithErrorForEchoWithoutArguments() {
        var effect = apply(new Command("ECHO", List.of()));
        assertEquals(new Effect.Error("wrong number of arguments for 'echo' command"), effect);
    }

    @Test
    void respondsWithErrorForEchoWithTooManyArguments() {
        var effect = apply(new Command("ECHO", List.of("a", "b")));
        assertEquals(new Effect.Error("wrong number of arguments for 'echo' command"), effect);
    }

    @Test
    void respondsWithErrorForGetWithoutArguments() {
        var effect = apply(new Command("GET", List.of()));
        assertEquals(new Effect.Error("wrong number of arguments for 'get' command"), effect);
    }

    @Test
    void respondsWithErrorForGetWithTooManyArguments() {
        var effect = apply(new Command("GET", List.of("a", "b")));
        assertEquals(new Effect.Error("wrong number of arguments for 'get' command"), effect);
    }

    @Test
    void respondsWithErrorForSetWithMissingValue() {
        var effect = apply(new Command("SET", List.of("a")));
        assertEquals(new Effect.Error("wrong number of arguments for '" + SetCommand.NAME + "' command"), effect);
    }

    @Test
    void acceptsSetWithExpiryOption() {
        var effect = apply(new Command("SET", List.of("a", "b", "PX", "5000")));
        assertEquals(Messages.ok(), effect);
    }

    @Test
    void respondsWithSyntaxErrorForSetExpiryWithoutTime() {
        var effect = apply(new Command("SET", List.of("a", "b", "PX")));
        assertEquals(new Effect.Error("syntax error"), effect);
    }

    @Test
    void respondsWithSyntaxErrorForSetWithBothExAndPx() {
        var effect = apply(new Command("SET", List.of("a", "b", "EX", "5", "PX", "5000")));
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
