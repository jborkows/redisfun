import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

public final class RedisServer implements AutoCloseable {
  private final int requestedPort;
  private ServerSocket serverSocket;
  private Thread acceptThread;
  private volatile boolean running;

  public RedisServer(int port) {
    this.requestedPort = port;
  }

  /**
   * Creates a server that will bind to a random free port when started.
   * Use {@link #getPort()} after {@link #start()} to learn the actual port.
   */
  public static RedisServer onRandomPort() {
    return new RedisServer(0);
  }

  public synchronized void start() throws IOException {
    if (running) {
      return;
    }
    serverSocket = new ServerSocket(requestedPort);
    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.setReuseAddress(true);
    running = true;
    // Non-daemon thread: keeps the JVM alive when started from Main,
    // while close() in tests shuts it down cleanly.
    acceptThread = new Thread(this::acceptLoop, "redis-server-accept");
    acceptThread.start();
  }

  /**
   * Returns the port the server is actually bound to.
   * Differs from the requested port when the server was created via {@link #onRandomPort()}.
   */
  public int getPort() {
    if (serverSocket == null) {
      throw new IllegalStateException("Server is not started");
    }
    return serverSocket.getLocalPort();
  }

  private void acceptLoop() {
    while (running) {
      try {
        var clientSocket = serverSocket.accept();
        handleClient(clientSocket);
      } catch (IOException e) {
        if (running) {
          Logger.error("IOException: " + e.getMessage());
        }
        // otherwise: socket closed by close(), shutting down
      }
    }
  }

  private void handleClient(Socket clientSocket) {
    try (clientSocket;) {
      var dispatcher = new Dispatcher(clientSocket.getInputStream(), clientSocket.getOutputStream());
      dispatcher.process();
    } catch (IOException e) {
      Logger.error("IOException: " + e.getMessage());
    }
  }

  @Override
  public synchronized void close() throws IOException {
    running = false;
    if (serverSocket != null) {
      serverSocket.close(); // unblocks accept()
    }
    if (acceptThread != null) {
      try {
        acceptThread.join(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
