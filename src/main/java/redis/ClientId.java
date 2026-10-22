package redis;

import java.util.concurrent.atomic.AtomicLong;

public record ClientId(long id) {
    public static final AtomicLong CLIENT_ID = new AtomicLong(0);

    public static ClientId generate() {
        return new ClientId(CLIENT_ID.incrementAndGet());
    }
}
