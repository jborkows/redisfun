package redis.storage;

import java.util.concurrent.CompletableFuture;

public final class FutureWithTimeoutCleanupBuilder<T> {

    private FutureWithTimeoutCleanupBuilder() {
    }

    public static <T> ForFuture<T> forFuture(CompletableFuture<T> future) {
        return new ForFuture<>(future);
    }

    public static final class ForFuture<T> {
        private final CompletableFuture<T> future;

        private ForFuture(CompletableFuture<T> future) {
            this.future = future;
        }

        public WithSuccess<T> onSuccess(Runnable onSuccess) {
            return new WithSuccess<>(future, onSuccess);
        }

        public FutureWithTimeoutCleanup<T> onlyCleaning(Runnable cleaningAction) {
            return new FutureWithTimeoutCleanup<>(future, cleaningAction);
        }
    }

    public static final class WithSuccess<T> {
        private final CompletableFuture<T> future;
        private final Runnable onSuccess;

        private WithSuccess(CompletableFuture<T> future, Runnable onSuccess) {
            this.future = future;
            this.onSuccess = onSuccess;
        }

        public FutureWithTimeoutCleanup<T> withCleaning(Runnable cleaningAction) {
            return new FutureWithTimeoutCleanup<>(future, cleaningAction, onSuccess);
        }
    }
}
