package redis;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Central place for JUL setup, shared by {@code Main} and integration tests.
 * Idempotent: calling it again just reconfigures the root logger.
 */
public final class Logging {

    private Logging() {
    }

    /**
     * Configures logging from the {@code log.level} system property:
     * {@code -Dlog.level=debug|info|error} (default: debug).
     * A system property is used so it never clashes with CLI args like {@code --port}.
     */
    public static void configureFromSystemProperty() {
        configure(System.getProperty("log.level", "debug"));
    }

    /**
     * Configures the root logger so every class logger inherits the level and format.
     * The level must be set on both the logger and the handler — JUL filters on both.
     */
    public static void configure(String levelName) {
        var level = parseLevel(levelName);
        var root = Logger.getLogger("");
        for (var handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        var handler = new ConsoleHandler();
        handler.setFormatter(new RedisFormatter());
        handler.setLevel(level);
        root.addHandler(handler);
        root.setLevel(level);
    }

    /**
     * Maps the app's level names to JUL levels: debug -&gt; FINE, info -&gt; INFO, error -&gt; SEVERE.
     */
    private static Level parseLevel(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "debug" -> Level.FINE;
            case "info" -> Level.INFO;
            case "error" -> Level.SEVERE;
            default -> throw new IllegalArgumentException(
                    "Unknown log level: '" + value + "' (expected debug, info or error)");
        };
    }

    /**
     * Custom format: {@code yyyy-MM-dd HH:mm:ss.SSS [LEVEL] [thread-N] message}.
     */
    private static final class RedisFormatter extends Formatter {
        private static final DateTimeFormatter TIMESTAMP_FORMAT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        @Override
        public String format(LogRecord record) {
            var timestamp = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(record.getMillis()), ZoneId.systemDefault())
                    .format(TIMESTAMP_FORMAT);
            return "%s [%-5s] [thread-%d] %s%n".formatted(
                    timestamp, displayName(record.getLevel()), record.getLongThreadID(), record.getMessage());
        }

        private static String displayName(Level level) {
            if (level == Level.FINE) {
                return "DEBUG";
            }
            if (level == Level.SEVERE) {
                return "ERROR";
            }
            return level.getName();
        }
    }
}
