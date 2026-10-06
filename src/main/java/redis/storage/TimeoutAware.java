package redis.storage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@FunctionalInterface
public interface TimeoutAware<T, V> {
    V apply(T value) throws TimeoutException, InterruptedException, ExecutionException;
}
