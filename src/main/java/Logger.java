import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Logger {
  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  private Logger() {
  }

  public static void info(String message) {
    log("INFO", message);
  }

  public static void error(String message) {
    log("ERROR", message);
  }

  private static void log(String level, String message) {
    String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
    long threadId = Thread.currentThread().threadId();
    System.out.printf("%s [%-5s] [thread-%d] %s%n", timestamp, level, threadId, message);
  }
}
