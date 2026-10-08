package redis.storage;

import org.jspecify.annotations.NonNull;
import redis.storage.StorageException.StreamOperationsException;
import redis.storage.Value.StreamValue;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

final class Streams {

    private final Storage storage;

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
        return id.get();
    }

    private StorageEntry validate(Entry entry, StreamValue streamValue) throws StreamOperationsException {
        var last = streamValue.value().isEmpty() ? null : streamValue.value().getLast();
        return StorageEntry.convert(entry, last);
    }

}
