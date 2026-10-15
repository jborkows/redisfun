package redis.storage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public record FutureWithTimeoutCleanup<T>(CompletableFuture<T> future, Runnable cleaningAction) {
    public T work(TimeoutAware<CompletableFuture<T>, T> operation) throws TimeoutException, InterruptedException, ExecutionException {
        try {
            return operation.apply(future);
        } catch (TimeoutException e) {
            cleaningAction.run();
            throw e;
        }
    }

    public <V> FutureWithTimeoutCleanup<V> map(Function<T, V> mapper) {
        return new FutureWithTimeoutCleanup<>(future.thenApply(mapper), cleaningAction);
    }
}
