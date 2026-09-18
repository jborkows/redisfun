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

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

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
        if (b == '\n') {
            return ready(finishLine());
        }
        if (buffer.size() == 0 && b == '*') {
            return ready(new Star());
        }
        if (buffer.size() == 0 && b == '$') {
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
            return new InvalidToken(bytes);
        }
        var line = new String(bytes, 0, last, StandardCharsets.US_ASCII);
        if (!isDigits(line)) {
            return new Text(line);
        }
        try {
            return new Length(Integer.parseInt(line));
        } catch (NumberFormatException e) {
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
