package redis.storage;

import org.jspecify.annotations.NonNull;
import redis.storage.Value.StreamValue;

import java.util.ArrayList;
import java.util.logging.Logger;

final class Streams {

    private static final Logger LOGGER = Logger.getLogger(Streams.class.getName());

    private final Storage storage;

    Streams(Storage storage) {
        this.storage = storage;
    }

    @NonNull
    private static StreamValue expect(@NonNull Value value) {
        return switch (value) {
            case StreamValue s -> s;
            default -> throw new StorageException.OperationAllowedOnlyForStreams();
        };
    }


    /**
     *
     * @param streamKey name of stream
     * @param entry     entry added to stream identified with streamKey
     * @return entryId
     */
    public String add(String streamKey, Entry entry) {
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
            streamValue.value().add(entry);
            return newPersistency;
        });
        return entry.id();
    }

}
