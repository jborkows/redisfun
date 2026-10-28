package redis;

import redis.commands.Effect;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

public final class RedisServer implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(RedisServer.class.getName());
    private static final int DEFAULT_MAX_CLIENTS = Integer.getInteger("redis.maxclients", 1000);
    private final int requestedPort;
    private final Semaphore clientSlots;
    private final Dispatcher dispatcher = new Dispatcher();
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;

    public RedisServer(int port) {
        this(port, DEFAULT_MAX_CLIENTS);
    }

    RedisServer(int port, int maxClients) {
        this.requestedPort = port;
        this.clientSlots = new Semaphore(maxClients);
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
                // Always accept once: blocking here (backpressure) would stall
                // the accept loop and make *every* new connection hang. Instead,
                // keep the loop free and reject only once the cap is exceeded.
                if (!clientSlots.tryAcquire()) {
                    reject(clientSocket);
                    continue;
                }
                Thread.ofVirtual().start(() -> {
                    try {
                        handleClient(clientSocket);
                    } finally {
                        // Release must happen even when the client vanishes
                        // mid-read (handleClient swallows IOException), or
                        // dead connections would slowly consume all slots.
                        clientSlots.release();
                    }
                });
            } catch (IOException e) {
                if (running) {
                    logger.severe("IOException: " + e.getMessage());
                }
                // otherwise: socket closed by close(), shutting down
            }
        }
    }

    /**
     * Redis-style maxclients behavior: when the connection cap is exceeded,
     * accept once (to be able to reply), send an explicit error reply and
     * close the socket. No handler thread and no slot are consumed.
     */
    private void reject(Socket clientSocket) {
        try (clientSocket) {
            clientSocket.getOutputStream().write(
                    ("-ERR max number of clients reached" + Effect.CRLF)
                            .getBytes(StandardCharsets.ISO_8859_1));
            clientSocket.getOutputStream().flush();
        } catch (IOException e) {
            // The reject path must never disturb the accept loop; a dead
            // client here is simply dropped.
            logger.fine("Failed to reject a client: " + e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) {
        var buffer = new byte[1024];
        int bytesRead;
        try (clientSocket) {
            var context = dispatcher.newConnection();
            context.bind(clientSocket.getOutputStream());
            while ((bytesRead = clientSocket.getInputStream().read(buffer)) != -1) {
                dispatcher.process(buffer, bytesRead, context);
            }
        } catch (IOException e) {
            logger.severe("IOException: " + e.getMessage());
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
        dispatcher.close();
    }
}
