package redis.storage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public record FutureWithTimeoutCleanup<T>(CompletableFuture<T> future, Runnable cleaningAction, Runnable onSuccess) {
    public FutureWithTimeoutCleanup(CompletableFuture<T> future, Runnable cleaningAction) {
        this(future, cleaningAction, () -> {
        });
    }

    public T work(TimeoutAware<CompletableFuture<T>, T> operation) throws TimeoutException, InterruptedException, ExecutionException {
        try {
            T result = operation.apply(future);
            onSuccess.run();
            return result;
        } catch (Exception e) {
            cleaningAction.run();
            throw e;
        }
    }

    public <V> FutureWithTimeoutCleanup<V> map(Function<T, V> mapper) {
        return new FutureWithTimeoutCleanup<>(future.thenApply(mapper), cleaningAction, onSuccess);
    }

    public static <T> FutureWithTimeoutCleanupBuilder.ForFuture<T> builder(CompletableFuture<T> future) {
        return FutureWithTimeoutCleanupBuilder.forFuture(future);
    }
}
