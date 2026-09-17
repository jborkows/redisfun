import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisServerIntegrationTest {

  private static RedisServer server;

  @BeforeAll
  static void startServer() throws IOException {
    // Random free port, never the application default (6379)
    server = RedisServer.onRandomPort();
    server.start();
  }

  @AfterAll
  static void stopServer() throws IOException {
    if (server != null) {
      server.close();
    }
  }

  @Test
  void respondsWithPongToPing() throws IOException {
    var response = sendCommand("*1","$4","PING");
    assertEquals("+PONG", response);
  }

  private String sendCommand(String ...command) throws IOException {
    try (var socket = new Socket("localhost", server.getPort());
         var in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
      for (var cmd : command) {
        socket.getOutputStream().write((cmd + "\r\n").getBytes());
      }
      socket.getOutputStream().flush();
      return in.readLine();
    }
  }
}
