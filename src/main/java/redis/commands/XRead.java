package redis.commands;

import redis.storage.Storage;
import redis.storage.StorageException;

import java.util.List;

public final class XRead {

    public static final String NAME = "XREAD";

    private XRead() {
    }

    public static Effect execute(List<String> args) {
        if (args.size() < 3 || args.size() % 2 == 0) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var operation = args.get(0);
        if (!"STREAMS".equalsIgnoreCase(operation)) {
            return Messages.errorMessage("Not known operation %s".formatted(operation));
        }
        var key = args.get(1);
        var id = args.get(2);

        try {
            var result = Storage.STORAGE.readStreamFrom(key, id);
            var entries = ReadStorageEntries.readEntries(result);
            return Messages.array(Messages.array(Messages.bulkMessage(key), entries));
        } catch (StorageException.StreamOperationsException.InvalidId e) {
            return Messages.wrongType("Used wrong ids");
        }
    }
}
