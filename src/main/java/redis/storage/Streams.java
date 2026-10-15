package redis.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import redis.storage.StorageException.StreamOperationsException;
import redis.storage.StorageException.StreamOperationsException.OperationAllowedOnlyForStreams;
import redis.storage.Value.StreamValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

final class Streams {

    private final Storage storage;
    private final Map<String, Map<StorageEntryKey, List<CompletableFuture<List<StorageEntry>>>>> pops = new ConcurrentHashMap<>();

    Streams(Storage storage) {
        this.storage = storage;
    }

    @NonNull
    private static StreamValue expect(@NonNull Value value) {
        return switch (value) {
            case StreamValue s -> s;
            default -> throw new StreamOperationsException.OperationAllowedOnlyForStreams();
        };
    }


    /**
     *
     * @param streamKey name of stream
     * @param entry     entry added to stream identified with streamKey
     * @return entryId
     */
    public String add(String streamKey, Entry entry) throws StreamOperationsException {
        var id = new AtomicReference<String>();
        storage.mutate(streamKey, (aKey, v) -> {
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
            id.set(validEntry.id());
            streamValue.value().add(validEntry);
            return newPersistency;
        });
        Thread.ofVirtual().start(() -> doBlockingRead(streamKey));
        return id.get();
    }

    private StorageEntry validate(Entry entry, StreamValue streamValue) throws StreamOperationsException {
        var last = streamValue.value().isEmpty() ? null : streamValue.value().getLast();
        return StorageEntry.convert(entry, last);
    }

    @NullMarked
    public List<StorageEntry> range(String key, String start, String end) {
        var startKey = StorageEntryKey.parse(start);
        var endKey = StorageEntryKey.parse(end);
        return query(key, i -> i.key().compareTo(startKey) >= 0 && i.key().compareTo(endKey) <= 0);
    }

    @NullMarked
    public List<StorageEntry> readSince(String key, String id) {
        var startKey = StorageEntryKey.parse(id);
        return query(key, i -> i.key().compareTo(startKey) > 0);
    }

    private List<StorageEntry> query(String key, Predicate<StorageEntry> predicate) {
        var snapshot = storage.readIfPresent(key, value -> List.copyOf(expect(value).value()));
        if (snapshot == null || snapshot.isEmpty()) {
            return List.of();
        }
        return snapshot.stream().filter(predicate).toList();
    }

    public FutureWithTimeoutCleanup<List<StorageEntry>> blockingRead(String key, String id) {
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
        var future = new CompletableFuture<List<StorageEntry>>();
        pops.compute(key, (_, acc) -> {
            if (acc == null) {
                acc = new HashMap<>();
            }
            acc.computeIfAbsent(startKey, (x) -> new ArrayList<>()).add(future);
            return acc;
        });
        doBlockingRead(key);

        return new FutureWithTimeoutCleanup<>(future, () ->
                pops.compute(key, (_, awaiting) -> {
                    if (awaiting == null) {
                        return null;
                    }
                    if (!awaiting.containsKey(startKey)) {
                        return awaiting;
                    }
                    awaiting.get(startKey).remove(future);
                    if (awaiting.isEmpty()) {
                        return null;
                    } else {
                        return awaiting;
                    }
                })
        );
    }

    private void doBlockingRead(@NonNull String key) {
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
                    //No matching keys, waiting for another chance
                    if (matchingKeys.isEmpty()) {
                        return acc;
                    }
                    for (var matchingKey : matchingKeys) {
                        var found = elements.stream().filter(i -> matchingKey.compareTo(i.key()) < 0).toList();
                        acc.remove(matchingKey).forEach(f -> f.complete(found));
                    }
                    if (acc.isEmpty()) {
                        return null;
                    }
                    return acc;
                });
                return existing;
            }
            return existing;
        });
    }
}
