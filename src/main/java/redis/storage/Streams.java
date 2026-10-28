package redis.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import redis.commands.EffectChannel;
import redis.commands.Scheduler;
import redis.storage.StorageException.StreamOperationsException;
import redis.storage.StorageException.StreamOperationsException.OperationAllowedOnlyForStreams;
import redis.storage.Value.StreamValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class Streams {

    private final Storage storage;
    private final Map<Key, Map<StorageEntryKey, List<Waiter<List<StorageEntry>>>>> pops = new HashMap<>();

    Streams(Storage storage) {
        this.storage = storage;
    }

    @NonNull
    private static StreamValue expect(@NonNull Value value) {
        if (value instanceof StreamValue s) {
            return s;
        }
        throw new OperationAllowedOnlyForStreams();
    }

    public EntryId add(Key streamKey, Entry entry) throws StreamOperationsException {
        var id = new EntryId[]{null};
        storage.mutate(streamKey, (_, v) -> {
            StreamValue streamValue;
            DataPersistency newPersistency;
            if (v == null) {
                streamValue = new StreamValue(new ArrayList<>());
                newPersistency = new DataPersistency.Eternal(streamValue);
            } else {
                var persistency = Storage.extractFrom(v);
                streamValue = expect(persistency);
                newPersistency = v;
            }
            var validEntry = validate(entry, streamValue);
            id[0] = new EntryId(validEntry.id());
            streamValue.value().add(validEntry);
            return newPersistency;
        });
        doBlockingRead(streamKey);
        return id[0];
    }

    private StorageEntry validate(Entry entry, StreamValue streamValue) throws StreamOperationsException {
        var last = streamValue.value().isEmpty() ? null : streamValue.value().getLast();
        return StorageEntry.convert(entry, last);
    }

    @NullMarked
    public List<StorageEntry> range(Key key, String start, String end) {
        var startKey = StorageEntryKey.parse(start);
        var endKey = StorageEntryKey.parse(end);
        return query(key, i -> i.key().compareTo(startKey) >= 0 && i.key().compareTo(endKey) <= 0);
    }

    @NullMarked
    public List<StorageEntry> readSince(Key key, String id) {
        StorageEntryKey startKey;
        if ("$".equals(id)) {
            var existing = storage.lookup(key);
            startKey = existing instanceof Value.StreamValue(List<StorageEntry> elements) && !elements.isEmpty()
                    ? elements.getLast().key()
                    : new StorageEntryKey(0, 0);
        } else {
            startKey = StorageEntryKey.parse(id);
        }
        return query(key, i -> i.key().compareTo(startKey) > 0);
    }

    private List<StorageEntry> query(Key key, Predicate<StorageEntry> predicate) {
        var snapshot = storage.readIfPresent(key, value -> List.copyOf(expect(value).value()));
        if (snapshot == null || snapshot.isEmpty()) {
            return List.of();
        }
        return snapshot.stream().filter(predicate).toList();
    }

    void blockingRead(Key key, String id, EffectChannel channel, Consumer<List<Entry>> onValue, Runnable onTimeout,
                      Scheduler scheduler, long timeoutMs) {
        var existing = storage.lookup(key);
        if (existing != null && !(existing instanceof StreamValue)) {
            throw new OperationAllowedOnlyForStreams();
        }
        StorageEntryKey startKey;
        if ("$".equals(id)) {
            startKey = existing == null
                    ? new StorageEntryKey(0, 0)
                    : ((StreamValue) existing).value().getLast().key();
        } else {
            startKey = StorageEntryKey.parse(id);
        }
        Waiter<List<StorageEntry>> waiter = new Waiter<>(channel, entries -> onValue.accept(Storage.intoEntries(entries)), onTimeout);
        pops.compute(key, (_, acc) -> {
            if (acc == null) {
                acc = new HashMap<>();
            }
            acc.computeIfAbsent(startKey, _ -> new ArrayList<>()).add(waiter);
            return acc;
        });
        doBlockingRead(key);
        if (timeoutMs > 0) {
            scheduler.schedule(() -> timeoutWaiter(key, startKey, waiter), timeoutMs);
        }
    }

    private void timeoutWaiter(Key key, StorageEntryKey startKey, Waiter<List<StorageEntry>> waiter) {
        if (waiter.completed) {
            return;
        }
        waiter.completed = true;
        pops.compute(key, (_, awaiting) -> {
            if (awaiting == null) {
                return null;
            }
            if (!awaiting.containsKey(startKey)) {
                return awaiting;
            }
            awaiting.get(startKey).remove(waiter);
            if (awaiting.isEmpty()) {
                return null;
            } else {
                return awaiting;
            }
        });
        waiter.onTimeout.run();
    }

    private void doBlockingRead(@NonNull Key key) {
        storage.mutate(key, (aKey, existing) -> {
            if (existing == null) {
                return null;
            }
            if (Storage.extractFrom(existing) instanceof Value.StreamValue(List<StorageEntry> elements)) {
                pops.compute(aKey, (_, acc) -> {
                    if (acc == null) {
                        return null;
                    }
                    if (elements.isEmpty()) {
                        return acc;
                    }
                    var lastKey = elements.getLast().key();
                    var matchingKeys = acc.keySet().stream().filter(id -> id.compareTo(lastKey) < 0).toList();
                    if (!matchingKeys.isEmpty()) {
                        for (var matchingKey : matchingKeys) {
                            var found = elements.stream().filter(i -> matchingKey.compareTo(i.key()) < 0).toList();
                            acc.remove(matchingKey).forEach(waiter -> {
                                waiter.completed = true;
                                waiter.onValue.accept(found);
                            });
                        }
                        if (acc.isEmpty()) {
                            return null;
                        }
                    }
                    return acc;
                });
                return existing;
            }
            return existing;
        });
    }

    void closeConnection(@NonNull EffectChannel channel) {
        var keysToRemove = new ArrayList<Key>();
        pops.forEach((key, awaiting) -> {
            var changed = false;
            for (var entry : awaiting.entrySet()) {
                var waiters = entry.getValue();
                waiters.removeIf(waiter -> {
                    if (waiter.channel == channel) {
                        waiter.completed = true;
                        return true;
                    }
                    return false;
                });
                if (waiters.isEmpty()) {
                    entry.setValue(null);
                    changed = true;
                }
            }
            if (changed) {
                awaiting.values().removeIf(Objects::isNull);
            }
            if (awaiting.isEmpty()) {
                keysToRemove.add(key);
            }
        });
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
}
