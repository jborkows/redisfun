package redis.storage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public record FutureWithTimeoutCleanup<T>(CompletableFuture<T> future, Runnable cleaningAction) {
    public T work(TimeoutAware<CompletableFuture<T>, T> operation) throws TimeoutException, InterruptedException, ExecutionException {
        try {
            return operation.apply(future);
        } catch (TimeoutException e) {
            cleaningAction.run();
            throw e;
        }
    }
}
