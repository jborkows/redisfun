import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;


enum DataType {
    STRING,
    LIST,
    NONE;

    static DataType parse(@Nullable Value value) {
        if (value == null) {
            return NONE;
        }
        return switch (value) {
            case ListValue _ -> LIST;
            case StringValue _ -> STRING;
        };
    }
}

sealed interface DataPersistency {

}

sealed interface Value {

}

@FunctionalInterface
interface TimeoutAware<T, V> {
    V apply(T value) throws TimeoutException, InterruptedException, ExecutionException;
}

final class Storage {

    static final Storage STORAGE = new Storage();
    private static final Logger LOGGER = Logger.getLogger(Storage.class.getName());
    private static final Map<String, DataPersistency> db = new ConcurrentHashMap<>();
    private static final Map<String, List<CompletableFuture<String>>> pops = new ConcurrentHashMap<>();

    private static Value extractFrom(DataPersistency persistency) {
        return switch (persistency) {
            case Eternal eternal -> eternal.value();
            case ExpiredAt expiredAt -> expiredAt.value();
        };
    }

    private static int normalizeIndex(@NonNull List<String> values, int index) {
        if (index >= values.size()) {
            return index;
        }
        if (index >= 0) {
            return index;
        }
        index = -index;
        if (index >= values.size()) {
            return 0;
        }
        return values.size() - index;
    }

    private static void doBlockingPop(@NonNull String key) {
        db.compute(key, (aKey, existing) -> {
            if (existing == null) {
                return null;
            }
            switch (extractFrom(existing)) {
                case ListValue listValue -> {
                    var elements = listValue.value();
                    if (elements.isEmpty()) {
                        return null;
                    }
                    pops.compute(aKey, (_, acc) -> {
                        if (acc == null) {
                            return null;
                        }
                        var howMany = Math.min(elements.size(), acc.size());
                        while (howMany-- > 0) {
                            var toComplete = acc.removeFirst();
                            LOGGER.finer(() -> "Completing blocking for key = " + aKey);
                            toComplete.complete(elements.removeFirst());
                        }
                        return acc;
                    });
                    return existing;
                }
                case StringValue _ -> {
                    return existing;
                }
            }
        });
    }

    @Nullable
    private static Value findValueFor(String key) {

        var found = db.computeIfPresent(key, (k, v) -> switch (v) {
            case Eternal eternal -> eternal;
            case ExpiredAt expiredAt -> System.currentTimeMillis() > expiredAt.timestamp() ? null : expiredAt;
        });
        if (found == null) {
            return null;
        }
        return extractFrom(found);

    }

    String get(@NonNull String key) throws StorageException {
        var value = findValueFor(key);
        if (value == null) {
            return null;
        }
        return switch (value) {
            case ListValue _ -> throw new OperationAllowedOnlyForSingleElement();
            case StringValue stringValue -> stringValue.value();
        };
    }

    void put(@NonNull String key, String value) throws StorageException {
        insert(key, new Eternal(new StringValue(value)));
    }

    void putExpirable(String key, String value, @NonNull Duration duration) throws StorageException {
        var future = System.currentTimeMillis() + duration.toMillis();
        insert(key, new ExpiredAt(new StringValue(value), future));
    }

    private void insert(@NonNull String key, @NonNull DataPersistency data) throws StorageException {
        db.compute(key, (k, v) -> {
            if (v == null) {
                return data;
            }
            var value = extractFrom(v);
            return switch (value) {
                case ListValue _ -> throw CannotModifyType.cannotModifyFromList();
                case StringValue _ -> data;
            };
        });
        Thread.ofVirtual().start(() -> doBlockingPop(key));

    }

    int addElement(@NonNull String key, @NonNull List<String> values) {
        return operateOnList(key, values, (list) -> {
            list.addAll(values);
        });
    }

    int prependElements(@NonNull String key, @NonNull List<String> values) {
        var front = values.reversed();
        return operateOnList(key, front, (list) -> list.addAll(0, front));
    }

    private int operateOnList(@NonNull String key, @NonNull List<String> values, @NonNull Consumer<List<String>> merger) {
        var wrapper = new AtomicInteger(0);
        db.compute(key, (k, v) -> {
            if (v == null) {
                wrapper.set(values.size());
                return new Eternal(new ListValue(new ArrayList<>(values)));
            }
            return switch (extractFrom(v)) {
                case ListValue listValue -> {
                    merger.accept(listValue.value());
                    wrapper.set(listValue.value().size());
                    yield v;
                }
                case StringValue _ -> throw CannotModifyType.cannotModifyFromElement();
            };
        });
        Thread.ofVirtual().start(() -> doBlockingPop(key));
        return wrapper.get();
    }

    public @NonNull List<String> sublist(@NonNull String key, int start, int stop) {
        var data = findValueFor(key);
        return switch (data) {
            case null -> List.of();
            case ListValue(List<String> values) -> {
                if (values.isEmpty()) {
                    yield List.of();
                }
                var normalizedStart = normalizeIndex(values, start);
                var normalizedStop = Math.min(normalizeIndex(values, stop) + 1, values.size());
                LOGGER.finer(() -> "For " + key + " start = " + start + " normalized = " + normalizedStart + " stop = " + stop + " normalized = " + normalizedStop);
                if (normalizedStart >= values.size()) {
                    yield List.of();
                }
                if (normalizedStart > normalizedStop) {
                    yield List.of();
                } else {
                    yield List.copyOf(values.subList(normalizedStart, normalizedStop));
                }
            }
            default -> throw new OperationAllowedOnlyForLists();
        };
    }

    public int lengthOf(@NonNull String key) {
        var found = findValueFor(key);
        return switch (found) {
            case null -> 0;
            case ListValue list -> list.value().size();
            case StringValue _ -> throw new OperationAllowedOnlyForLists();
        };
    }

    @Nullable
    public List<String> pop(@NonNull String key, int howMany) {
        if (howMany < 1) {
            throw new OnlyPositiveValues();
        }
        var reference = new AtomicReference<List<String>>(null);
        db.computeIfPresent(key, (aKey, existing) -> {
            switch (extractFrom(existing)) {
                case ListValue listValue -> {
                    var elements = listValue.value();
                    if (elements.isEmpty()) {
                        reference.set(null);
                        return null;
                    }
                    if (howMany == 1) {
                        var found = elements.removeFirst();
                        reference.set(List.of(found));
                    } else if (howMany >= elements.size()) {
                        reference.set(elements);
                        return null;
                    } else {
                        var view = elements.subList(0, howMany);
                        reference.set(List.copyOf(view));
                        view.clear();
                    }
                    if (elements.isEmpty()) {
                        return null;
                    }
                    return existing;
                }
                case StringValue _ -> {
                    throw new OperationAllowedOnlyForLists();
                }
            }
        });
        return reference.get();
    }

    @NonNull
    public FutureWithTimeoutCleanup<String> blockingPop(@NonNull String key) {
        var future = new CompletableFuture<String>();
        LOGGER.finer(() -> "Blocking pop operation for " + key);
        pops.compute(key, (_, acc) -> {
            if (acc == null) {
                acc = new ArrayList<>();
            }
            acc.add(future);
            return acc;
        });
        doBlockingPop(key);
        return new FutureWithTimeoutCleanup<>(future, () -> {
            pops.compute(key, (aKey, awaiting) -> {
                if (awaiting == null) {
                    return null;
                }
                awaiting.remove(future);
                if (awaiting.isEmpty()) {
                    return null;
                } else {
                    return awaiting;
                }
            });
        }
        );
    }

    public DataType type(String key) {
        var wrapped = findValueFor(key);
        if (wrapped == null) {
            return DataType.NONE;
        }
        return DataType.parse(wrapped);
    }
}

sealed abstract class StorageException extends RuntimeException {

}

final class OperationAllowedOnlyForSingleElement extends StorageException {

}

final class OperationAllowedOnlyForLists extends StorageException {

}

final class OnlyPositiveValues extends StorageException {
}

final class CannotModifyType extends StorageException {
    final String existingType;

    CannotModifyType(String existingType) {
        this.existingType = existingType;
    }

    static CannotModifyType cannotModifyFromElement() {
        return new CannotModifyType("Element");
    }

    static CannotModifyType cannotModifyFromList() {
        return new CannotModifyType("List");
    }

}

record StringValue(String value) implements Value {
}

record ListValue(List<String> value) implements Value {
}

record Eternal(Value value) implements DataPersistency {
}

record ExpiredAt(Value value, long timestamp) implements DataPersistency {
}

record FutureWithTimeoutCleanup<T>(CompletableFuture<T> future, Runnable cleaningAction) {
    T work(TimeoutAware<CompletableFuture<T>, T> operation) throws TimeoutException, InterruptedException, ExecutionException {
        try {
            return operation.apply(future);
        } catch (TimeoutException e) {
            cleaningAction.run();
            throw e;
        }
    }
}
