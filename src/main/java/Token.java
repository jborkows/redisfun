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

    private enum Mode {
        LINE, RAW, AWAIT_CARRIAGE_RETURN, AWAIT_LINE_FEED
    }

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private Mode mode = Mode.LINE;
    private boolean expectBulkLength = false;
    private int rawRemaining = -1;

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
        return switch (mode) {
            case RAW -> onRawByte(b);
            case AWAIT_CARRIAGE_RETURN -> onAwaitCarriageReturn(b);
            case AWAIT_LINE_FEED -> onAwaitLineFeed(b);
            case LINE -> onLineByte(b);
        };
    }

    private TokenResult onRawByte(byte b) {
        buffer.write(b);
        rawRemaining--;
        if (rawRemaining == 0) {
            mode = Mode.AWAIT_CARRIAGE_RETURN;
        }
        return notReady();
    }

    private TokenResult onAwaitCarriageReturn(byte b) {
        if (b == '\r') {
            mode = Mode.AWAIT_LINE_FEED;
            return notReady();
        }
        return invalidRaw();
    }

    private TokenResult onAwaitLineFeed(byte b) {
        if (b == '\n') {
            var content = buffer.toString(StandardCharsets.ISO_8859_1);
            buffer.reset();
            mode = Mode.LINE;
            return ready(new Text(content));
        }
        return invalidRaw();
    }

    private TokenResult invalidRaw() {
        var content = buffer.toByteArray();
        buffer.reset();
        rawRemaining = -1;
        mode = Mode.LINE;
        return ready(new InvalidToken(content));
    }

    private TokenResult onLineByte(byte b) {
        if (b == '\n') {
            return ready(finishLine());
        }
        if (buffer.size() == 0 && b == '*') {
            return ready(new Star());
        }
        if (buffer.size() == 0 && b == '$') {
            expectBulkLength = true;
            return ready(new Dollar());
        }
        buffer.write(b);
        return notReady();
    }

    private Token finishLine() {
        var bytes = buffer.toByteArray();
        buffer.reset();
        var last = bytes.length - 1;
        if (last < 0 || bytes[last] != '\r') {
            expectBulkLength = false;
            return new InvalidToken(bytes);
        }
        var line = new String(bytes, 0, last, StandardCharsets.US_ASCII);
        if (!isDigits(line)) {
            if (expectBulkLength) {
                expectBulkLength = false;
                return new InvalidToken(bytes);
            }
            return new Text(line);
        }
        try {
            var value = Integer.parseInt(line);
            if (expectBulkLength) {
                expectBulkLength = false;
                rawRemaining = value;
                mode = value == 0 ? Mode.AWAIT_CARRIAGE_RETURN : Mode.RAW;
            }
            return new Length(value);
        } catch (NumberFormatException e) {
            expectBulkLength = false;
            return new InvalidToken(bytes);
        }
    }

    private TokenResult ready(Token token) {
        return new TokenResult(this, new TokenState.Ready(token));
    }

    private TokenResult notReady() {
        return new TokenResult(this, TokenState.NOT_READY);
    }
}

record TokenResult(Lexer lexer, TokenState state) {
}
