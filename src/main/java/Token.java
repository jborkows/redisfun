import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

sealed interface Token {
}

sealed interface TokenState {
    NotReady NOT_READY = new NotReady();

    record Ready(Token token) implements TokenState {
    }

    final class NotReady implements TokenState {
    }
}

final class Star implements Token {
}

record Length(int value) implements Token {
}

final class Dollar implements Token {
}

record Text(String value) implements Token {
}

record InvalidToken(byte[] bytes) implements Token {
}

class Lexer {

    private sealed interface LexerState {
        LexerStep onByte(byte b);
    }

    private record LexerStep(LexerState next, TokenState result) {
    }

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private LexerState state = new Line();

    private static boolean isDigits(String line) {
        if (line.isEmpty()) {
            return false;
        }
        for (var i = 0; i < line.length(); i++) {
            if (!Character.isDigit(line.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    TokenResult onByte(byte b) {
        var step = state.onByte(b);
        state = step.next();
        return new TokenResult(this, step.result());
    }

    private LexerStep step(LexerState next, TokenState result) {
        return new LexerStep(next, result);
    }

    private LexerStep invalidRaw() {
        var content = buffer.toByteArray();
        buffer.reset();
        return step(new Line(), new TokenState.Ready(new InvalidToken(content)));
    }

    private LexerStep finishLine(boolean expectBulkLength) {
        var bytes = buffer.toByteArray();
        buffer.reset();
        var last = bytes.length - 1;
        if (last < 0 || bytes[last] != '\r') {
            return step(new Line(), new TokenState.Ready(new InvalidToken(bytes)));
        }
        var line = new String(bytes, 0, last, StandardCharsets.US_ASCII);
        if (!isDigits(line)) {
            if (expectBulkLength) {
                return step(new Line(), new TokenState.Ready(new InvalidToken(bytes)));
            }
            return step(new Line(), new TokenState.Ready(new Text(line)));
        }
        try {
            var value = Integer.parseInt(line);
            if (expectBulkLength) {
                LexerState next = value == 0 ? new AwaitingCarriageReturn() : new Raw(value);
                return step(next, new TokenState.Ready(new Length(value)));
            }
            return step(new Line(), new TokenState.Ready(new Length(value)));
        } catch (NumberFormatException e) {
            return step(new Line(), new TokenState.Ready(new InvalidToken(bytes)));
        }
    }

    private final class Line implements LexerState {
        private final boolean expectBulkLength;

        Line() {
            this(false);
        }

        Line(boolean expectBulkLength) {
            this.expectBulkLength = expectBulkLength;
        }

        @Override
        public LexerStep onByte(byte b) {
            if (b == '\n') {
                return finishLine(expectBulkLength);
            }
            if (buffer.size() == 0 && b == '*') {
                return step(this, new TokenState.Ready(new Star()));
            }
            if (buffer.size() == 0 && b == '$') {
                return step(new Line(true), new TokenState.Ready(new Dollar()));
            }
            buffer.write(b);
            return step(this, TokenState.NOT_READY);
        }
    }

    private final class Raw implements LexerState {
        private final int remaining;

        Raw(int remaining) {
            this.remaining = remaining;
        }

        @Override
        public LexerStep onByte(byte b) {
            buffer.write(b);
            var next = remaining - 1;
            if (next == 0) {
                return step(new AwaitingCarriageReturn(), TokenState.NOT_READY);
            }
            return step(new Raw(next), TokenState.NOT_READY);
        }
    }

    private final class AwaitingCarriageReturn implements LexerState {
        @Override
        public LexerStep onByte(byte b) {
            if (b == '\r') {
                return step(new AwaitingLineFeed(), TokenState.NOT_READY);
            }
            return invalidRaw();
        }
    }

    private final class AwaitingLineFeed implements LexerState {
        @Override
        public LexerStep onByte(byte b) {
            if (b == '\n') {
                var content = buffer.toString(StandardCharsets.ISO_8859_1);
                buffer.reset();
                return step(new Line(), new TokenState.Ready(new Text(content)));
            }
            return invalidRaw();
        }
    }
}

record TokenResult(Lexer lexer, TokenState state) {
}
