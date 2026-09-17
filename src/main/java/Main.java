import java.io.IOException;
import java.util.logging.Logger;

public class Main {
  private static final Logger logger = Logger.getLogger(Main.class.getName());

  public static void main(String[] args) {
    Logging.configureFromSystemProperty();
    logger.info("Logs from your program will appear here!");

    int port = 6379;
    var server = new RedisServer(port);
    try {
      server.start();
      logger.info("Server started on port " + server.getPort());
      // The accept thread is non-daemon, so the JVM stays alive after main returns.
    } catch (IOException e) {
      logger.severe("IOException: " + e.getMessage());
    }
  }
}
