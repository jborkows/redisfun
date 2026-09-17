import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Main {
  public static void main(String[] args){
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    Logger.info("Logs from your program will appear here!");

    int port = 6379;
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      // Since the tester restarts your program quite often, setting SO_REUSEADDR
      // ensures that we don't run into 'Address already in use' errors
      serverSocket.setReuseAddress(true);
      // Wait for connection from client.
      try (var clientSocket = serverSocket.accept();
           var in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      ) {
        var line = in.readLine();
        Logger.info("Read: " + line);
        clientSocket.getOutputStream().write("+PONG\r\n".getBytes());
        // Handle client connection
      }
    } catch (IOException e) {
      Logger.error("IOException: " + e.getMessage());
    }
  }
}
