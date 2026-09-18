import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    @Test
    void parsesStarAsToken() {
        assertInstanceOf(Star.class, tokenizeOne("*"));
    }

    @Test
    void parsesDollarAsToken() {
        assertInstanceOf(Dollar.class, tokenizeOne("$"));
    }

    @Test
    void parseLength() {
        assertEquals(new Length(12), tokenizeOne("12\r\n"));
    }

    @Test
    void parsesLargeLength() {
        assertEquals(new Length(128), tokenizeOne("128\r\n"));
    }

    @Test
    void parseSimpleText() {
        assertEquals(new Text("1a"), tokenizeOne("1a\r\n"));
    }

    @Test
    void parseWithSpecialCharacters() {
        assertEquals(new Text("1*$"), tokenizeOne("1*$\r\n"));
    }

    @Test
    void parsesEmptyLineAsEmptyText() {
        assertEquals(new Text(""), tokenizeOne("\r\n"));
    }

    @Test
    void doesNotEmitTokenForIncompleteInput() {
        assertTrue(tokenize("12\r").isEmpty());
    }

    @Test
    void emitsCompletedTokensWhileRestIsIncomplete() {
        var tokens = tokenize("*1\r");
        assertEquals(1, tokens.size());
        assertInstanceOf(Star.class, tokens.getFirst());
    }

    @Test
    void parsesSequenceOfTokens() {
        var tokens = tokenize("*1\r\n");
        assertEquals(2, tokens.size());
        assertInstanceOf(Star.class, tokens.get(0));
        assertEquals(new Length(1), tokens.get(1));
    }

    @Test
    void rejectsBareNewline() {
        assertInstanceOf(InvalidToken.class, tokenizeOne("\n"));
    }

    @Test
    void rejectsLineWithoutTerminatingCarriageReturn() {
        assertInstanceOf(InvalidToken.class, tokenizeOne("1\ra\n"));
    }

    @Test
    void rejectsNumberTooLargeForInt() {
        assertInstanceOf(InvalidToken.class, tokenizeOne("9999999999\r\n"));
    }

    @Test
    void recoversAfterInvalidLine() {
        var tokens = tokenize("1\ra\n*");
        assertEquals(2, tokens.size());
        assertInstanceOf(InvalidToken.class, tokens.get(0));
        assertInstanceOf(Star.class, tokens.get(1));
    }

    private Token tokenizeOne(String input) {
        var tokens = tokenize(input);
        assertEquals(1, tokens.size(), () -> "Expected exactly one token but got: " + tokens);
        return tokens.getFirst();
    }

    private List<Token> tokenize(String input) {
        var lexer = new Lexer();
        var tokens = new ArrayList<Token>();
        for (byte b : input.getBytes(StandardCharsets.US_ASCII)) {
            var result = lexer.onByte(b);
            lexer = result.lexer();
            if (result.state() instanceof TokenState.Ready(Token token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
