package redis;

import org.jspecify.annotations.Nullable;
import redis.commands.Effect;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

public final class RedisServer implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(RedisServer.class.getName());
    private final int requestedPort;
    private final Semaphore clientSlots;
    private final Dispatcher dispatcher = new Dispatcher();
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;

    @Nullable
    private final Location replicaFor;

    public RedisServer(Config config) {
        this.requestedPort = config.port;
        this.clientSlots = new Semaphore(config.maxConnections);
        this.replicaFor = config.replicaFor;
    }

    /**
     * Creates a server that will bind to a random free port when started.
     * Use {@link #getPort()} after {@link #start()} to learn the actual port.
     */
    public static RedisServer onRandomPort() {
        return new RedisServer(new Config());
    }

    public static ConfigChanger portChanger(int port) {
        return new ConfigChanger.PortChanger(port);
    }

    public static ConfigChanger randomPort() {
        return new ConfigChanger.PortChanger(0);
    }

    public static ConfigChanger replicaOf(String address, int port) {
        return new ConfigChanger.ReplicaFor(address, port);
    }

    public static ConfigChanger maxConnectionsChanger(int maxConnections) {
        return new ConfigChanger.MaxConnectionsChanger(maxConnections);
    }

    public static RedisServer redisServer(ConfigChanger... configChangers) {
        var config = new Config();
        for (var configChanger : configChangers) {
            configChanger.modify(config);
        }
        return new RedisServer(config);
    }

    public static RedisServer redisServer(Collection<ConfigChanger> configChangers) {
        return redisServer(configChangers.toArray(new ConfigChanger[0]));
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
        var serverKind = replicaFor != null ? new ServerKind.Slave(replicaFor) : new ServerKind.Master();
        try (clientSocket; var handle = dispatcher.openConnection(serverKind)) {
            var context = handle.context();
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

    public sealed interface ConfigChanger {
        void modify(Config config);

        record PortChanger(int port) implements ConfigChanger {
            @Override
            public void modify(Config config) {
                config.port = port;
            }
        }

        record MaxConnectionsChanger(int maxConnections) implements ConfigChanger {
            @Override
            public void modify(Config config) {
                config.maxConnections = maxConnections;
            }
        }

        record ReplicaFor(String address, int port) implements ConfigChanger {
            @Override
            public void modify(Config config) {
                config.replicaFor = new Location(address, port);
            }
        }
    }

    public static class Config {

        int port;

        int maxConnections;

        @Nullable
        Location replicaFor;

        public Config(ConfigChanger... changes) {
            port = 0;
            maxConnections = Integer.getInteger("redis.maxclients", 1000);
            for (ConfigChanger changer : changes) {
                changer.modify(this);
            }
        }
    }
}

