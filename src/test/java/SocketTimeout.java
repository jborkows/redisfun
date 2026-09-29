import java.io.IOException;

final class SocketTimeout {
    private static final ThreadLocal<SocketTimeout> INSTANCE = new ThreadLocal<>();
    private static final int DEFAULT_TIMEOUT_MS = 300;
    private final int ms;

    private SocketTimeout() {
        this(DEFAULT_TIMEOUT_MS);
    }

    private SocketTimeout(int ms) {
        this.ms = ms;
    }

    static SocketTimeout get() {
        return INSTANCE.get();
    }

    static <T> T with(NetworkCall<T> runnable, int timeoutMs) throws IOException {
        var entity = INSTANCE.get();
        try {
            if (entity == null) {
                entity = new SocketTimeout(timeoutMs);
            }
            INSTANCE.set(entity);
            return runnable.get();
        } finally {
            INSTANCE.set(entity);
        }
    }

    static int timeoutMs() {
        var entity = INSTANCE.get();
        if (entity != null) {
            return entity.ms;
        } else {
            return DEFAULT_TIMEOUT_MS;
        }
    }
}
