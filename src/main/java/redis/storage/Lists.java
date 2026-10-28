package redis.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import redis.commands.EffectChannel;
import redis.commands.Scheduler;
import redis.storage.DataPersistency.Eternal;
import redis.storage.StorageException.CannotModifyType;
import redis.storage.StorageException.OnlyPositiveValues;
import redis.storage.StorageException.OperationAllowedOnlyForLists;
import redis.storage.Value.ListValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * All list-shaped operations, including blocking-pop waiters.
 * All access is serialised by the single actor thread.
 */
final class Lists {

    private static final Logger LOGGER = Logger.getLogger(Lists.class.getName());

    private final Storage storage;
    private final Map<Key, List<Waiter<String>>> pops = new HashMap<>();

    Lists(Storage storage) {
        this.storage = storage;
    }

    @NonNull
    private static ListValue expectList(@NonNull Value value) {
        if (value instanceof ListValue listValue) {
            return listValue;
        }
        throw new OperationAllowedOnlyForLists();
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

    int add(@NonNull Key key, @NonNull List<String> values) {
        return operateOnList(key, values, (list) -> list.addAll(values));
    }

    int prepend(@NonNull Key key, @NonNull List<String> values) {
        var front = values.reversed();
        return operateOnList(key, front, (list) -> list.addAll(0, front));
    }

    private int operateOnList(@NonNull Key key, @NonNull List<String> values, @NonNull Consumer<List<String>> merger) {
        var wrapper = new int[]{0};
        storage.mutate(key, (_, v) -> {
            if (v == null) {
                wrapper[0] = values.size();
                return new Eternal(new ListValue(new ArrayList<>(values)));
            }
            if (Storage.extractFrom(v) instanceof ListValue(List<String> list)) {
                merger.accept(list);
                wrapper[0] = list.size();
                return v;
            }
            throw CannotModifyType.cannotModifyFromElement();
        });
        doBlockingPop(key);
        return wrapper[0];
    }

    @NonNull
    List<String> sublist(@NonNull Key key, int start, int stop) {
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
        LOGGER.finer(() -> "For " + key.value() + " start = " + start + " normalized = " + normalizedStart + " stop = " + stop + " normalized = " + normalizedStop);
        if (normalizedStart >= values.size() || normalizedStart > normalizedStop) {
            return List.of();
        }
        return List.copyOf(values.subList(normalizedStart, normalizedStop));
    }

    int length(@NonNull Key key) {
        var found = storage.lookup(key);
        if (found == null) {
            return 0;
        }
        return expectList(found).value().size();
    }

    @Nullable
    List<String> pop(@NonNull Key key, int howMany) {
        if (howMany < 1) {
            throw new OnlyPositiveValues();
        }
        var reference = new Reference<List<String>>();
        storage.mutateIfPresent(key, (_, existing) -> {
            var elements = expectList(Storage.extractFrom(existing)).value();
            if (elements.isEmpty()) {
                reference.value = null;
                return null;
            }
            if (howMany == 1) {
                var found = elements.removeFirst();
                reference.value = List.of(found);
            } else if (howMany >= elements.size()) {
                reference.value = elements;
                return null;
            } else {
                var view = elements.subList(0, howMany);
                reference.value = List.copyOf(view);
                view.clear();
            }
            if (elements.isEmpty()) {
                return null;
            }
            return existing;
        });
        return reference.value;
    }

    void blockingPop(@NonNull Key key, @NonNull EffectChannel channel, @NonNull Consumer<String> onValue,
                     @NonNull Runnable onTimeout, @NonNull Scheduler scheduler, long timeoutMs) {
        var waiter = new Waiter<>(channel, onValue, onTimeout);
        pops.compute(key, (_, acc) -> {
            if (acc == null) {
                acc = new ArrayList<>();
            }
            acc.add(waiter);
            return acc;
        });
        doBlockingPop(key);
        if (timeoutMs > 0) {
            scheduler.schedule(() -> timeoutWaiter(key, waiter), timeoutMs);
        }
    }

    private void timeoutWaiter(@NonNull Key key, @NonNull Waiter<String> waiter) {
        if (waiter.completed) {
            return;
        }
        waiter.completed = true;
        pops.compute(key, (_, acc) -> {
            if (acc == null) {
                return null;
            }
            acc.remove(waiter);
            return acc.isEmpty() ? null : acc;
        });
        waiter.onTimeout.run();
    }

    private void doBlockingPop(@NonNull Key key) {
        storage.mutate(key, (aKey, existing) -> {
            if (existing == null) {
                return null;
            }
            if (!(Storage.extractFrom(existing) instanceof ListValue(List<String> elements))) {
                return existing;
            }
            if (elements.isEmpty()) {
                return existing;
            }
            pops.compute(aKey, (_, acc) -> {
                if (acc == null) {
                    return null;
                }
                var howMany = Math.min(elements.size(), acc.size());
                while (howMany-- > 0) {
                    var waiter = acc.removeFirst();
                    waiter.completed = true;
                    LOGGER.finer(() -> "Completing blocking for key = " + aKey.value());
                    waiter.onValue.accept(elements.removeFirst());
                }
                return acc.isEmpty() ? null : acc;
            });
            storage.notifyWatchers(aKey);
            return elements.isEmpty() ? null : existing;
        });
    }

    void closeConnection(@NonNull EffectChannel channel) {
        var keysToRemove = new ArrayList<Key>();
        for (var entry : pops.entrySet()) {
            var key = entry.getKey();
            var waiters = entry.getValue();
            waiters.removeIf(waiter -> {
                if (waiter.channel == channel) {
                    waiter.completed = true;
                    return true;
                }
                return false;
            });
            if (waiters.isEmpty()) {
                keysToRemove.add(key);
            }
        }
        keysToRemove.forEach(pops::remove);
    }

    private static final class Waiter<T> {
        final EffectChannel channel;
        final Consumer<T> onValue;
        final Runnable onTimeout;
        boolean completed;

        Waiter(EffectChannel channel, Consumer<T> onValue, Runnable onTimeout) {
            this.channel = channel;
            this.onValue = onValue;
            this.onTimeout = onTimeout;
        }
    }

    private static final class Reference<T> {
        T value;
    }
}
