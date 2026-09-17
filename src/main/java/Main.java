import java.io.IOException;

public class Main {
  public static void main(String[] args) {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    Logger.info("Logs from your program will appear here!");

    int port = 6379;
    var server = new RedisServer(port);
    try {
      server.start();
      Logger.info("Server started on port " + server.getPort());
      // The accept thread is non-daemon, so the JVM stays alive after main returns.
    } catch (IOException e) {
      Logger.error("IOException: " + e.getMessage());
    }
  }
}
