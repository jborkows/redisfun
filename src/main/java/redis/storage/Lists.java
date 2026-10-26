package redis.storage;

import redis.storage.DataPersistency.Eternal;
import redis.storage.Value.ListValue;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import redis.storage.StorageException.CannotModifyType;
import redis.storage.StorageException.OnlyPositiveValues;
import redis.storage.StorageException.OperationAllowedOnlyForLists;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * All list-shaped operations, including blocking-pop waiters.
 * Atomicity comes from {@link Storage#mutate}; this class never touches the keyspace directly.
 */
final class Lists {

    private static final Logger LOGGER = Logger.getLogger(Lists.class.getName());

    private final Storage storage;
    private final Map<String, List<CompletableFuture<String>>> pops = new ConcurrentHashMap<>();

    Lists(Storage storage) {
        this.storage = storage;
    }

    @NonNull
    private static ListValue expectList(@NonNull Value value) {
        return switch (value) {
            case ListValue listValue -> listValue;
            default -> throw new OperationAllowedOnlyForLists();
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

    int add(@NonNull String key, @NonNull List<String> values) {
        return operateOnList(key, values, (list) -> list.addAll(values));
    }

    int prepend(@NonNull String key, @NonNull List<String> values) {
        var front = values.reversed();
        return operateOnList(key, front, (list) -> list.addAll(0, front));
    }

    private int operateOnList(@NonNull String key, @NonNull List<String> values, @NonNull Consumer<List<String>> merger) {
        var wrapper = new AtomicInteger(0);
        storage.mutate(key, (_, v) -> {
            if (v == null) {
                wrapper.set(values.size());
                return new Eternal(new ListValue(new ArrayList<>(values)));
            }
            return switch (Storage.extractFrom(v)) {
                case ListValue listValue -> {
                    merger.accept(listValue.value());
                    wrapper.set(listValue.value().size());
                    yield v;
                }
                default -> throw CannotModifyType.cannotModifyFromElement();
            };
        });
        Thread.ofVirtual().start(() -> doBlockingPop(key));
        return wrapper.get();
    }

    @NonNull
    List<String> sublist(@NonNull String key, int start, int stop) {
        var data = storage.lookup(key);
        if (data == null) {
            return List.of();
        }
        var values = expectList(data).value();
        if (values.isEmpty()) {
            return List.of();
        }
        var normalizedStart = normalizeIndex(values, start);
        var normalizedStop = Math.min(normalizeIndex(values, stop) + 1, values.size());
        LOGGER.finer(() -> "For " + key + " start = " + start + " normalized = " + normalizedStart + " stop = " + stop + " normalized = " + normalizedStop);
        if (normalizedStart >= values.size() || normalizedStart > normalizedStop) {
            return List.of();
        }
        return List.copyOf(values.subList(normalizedStart, normalizedStop));
    }

    int length(@NonNull String key) {
        var found = storage.lookup(key);
        if (found == null) {
            return 0;
        }
        return expectList(found).value().size();
    }

    @Nullable
    List<String> pop(@NonNull String key, int howMany) {
        if (howMany < 1) {
            throw new OnlyPositiveValues();
        }
        var reference = new AtomicReference<List<String>>(null);
        storage.mutateIfPresent(key, (_, existing) -> {
            var elements = expectList(Storage.extractFrom(existing)).value();
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
        });
        return reference.get();
    }

    @NonNull
    FutureWithTimeoutCleanup<String> blockingPop(@NonNull String key) {
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
        return FutureWithTimeoutCleanup.<String>builder(future)
                .onSuccess(() -> storage.notifyWatchers(new Key(key)))
                .withCleaning(() -> pops.compute(key, (_, awaiting) -> {
                    if (awaiting == null) {
                        return null;
                    }
                    awaiting.remove(future);
                    if (awaiting.isEmpty()) {
                        return null;
                    } else {
                        return awaiting;
                    }
                }));
    }

    private void doBlockingPop(@NonNull String key) {
        storage.mutate(key, (aKey, existing) -> {
            if (existing == null) {
                return null;
            }
            if (Storage.extractFrom(existing) instanceof ListValue(List<String> elements)) {
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
            return existing;
        });
    }
}
