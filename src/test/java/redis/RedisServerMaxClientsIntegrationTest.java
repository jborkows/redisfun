package redis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static redis.RedisServer.maxConnectionsChanger;

/**
 * Redis-style "maxclients" behavior of a dedicated server instance (not the
 * shared one, because this test must hold many connections open — while they
 * are held, every other client would be rejected).
 */
class RedisServerMaxClientsIntegrationTest {

    private static final int CAP = 5;
    private static final List<String> MAX_CLIENTS_ERROR =
            List.of("-ERR max number of clients reached");

    private static RedisServer startServerWith() throws IOException {
        var server = new RedisServer(new RedisServer.Config(maxConnectionsChanger(CAP)));
        server.start();
        return server;
    }

    /**
     * Reads one reply until the server closes the connection; the reject
     * path is a single burst (error line + close), so no timeout trickery
     * from {@link SharedRedisServer#sendCommand} is needed here.
     */
    private static List<String> readReply(Socket socket) throws IOException {
        socket.setSoTimeout(300);
        var response = new java.io.ByteArrayOutputStream();
        var buffer = new byte[1024];
        var in = socket.getInputStream();
        try {
            while (true) {
                var read = in.read(buffer);
                if (read == -1) {
                    break;
                }
                response.write(buffer, 0, read);
            }
        } catch (SocketTimeoutException ignored) {
            // server stopped sending; evaluate what has been received
        }
        var text = response.toString(StandardCharsets.ISO_8859_1);
        var lines = new ArrayList<>(Arrays.asList(text.split("\r\n", -1)));
        if (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        return lines;
    }

    @Test
    @Timeout(10)
    void clientExceedingTheCapGetsErrorAndConnectionIsClosed() throws IOException {
        try (var server = startServerWith()) {
            var held = new ArrayList<Socket>();
            try {
                // Hold exactly CAP connections open, silent (their handler
                // threads just park in read()).
                for (var i = 0; i < CAP; i++) {
                    held.add(new Socket("localhost", server.getPort()));
                }

                // The next client connects fine (the server always accepts
                // once) but its only reply is the error, followed by a close.
                var rejected = new Socket("localhost", server.getPort());
                assertEquals(MAX_CLIENTS_ERROR, readReply(rejected));
            } finally {
                for (var socket : held) {
                    socket.close();
                }
            }
        }
    }

    @Test
    @Timeout(10)
    void slotIsReleasedWhenClientVanishes() throws IOException, InterruptedException {
        try (var server = startServerWith()) {
            var held = new ArrayList<Socket>();
            try {
                for (var i = 0; i < CAP; i++) {
                    held.add(new Socket("localhost", server.getPort()));
                }
                // Everything vanishes (handler threads see EOF and release
                // their slots via the finally path).
                for (var socket : held) {
                    socket.close();
                }
                Thread.sleep(100);

                // A new connection must NOT be rejected. If it were, the
                // first read would return the error reply instead of timing
                // out with an empty stream.
                var reconnected = new Socket("localhost", server.getPort());
                reconnected.setSoTimeout(300);
                assertThrows(SocketTimeoutException.class, () -> reconnected.getInputStream().read(new byte[1]));
                // timed out on an empty stream → accepted and silent, i.e. the
                // slot was released correctly instead of being rejected/error'd
            } finally {
                for (var socket : held) {
                    socket.close();
                }
            }
        }
    }
}
