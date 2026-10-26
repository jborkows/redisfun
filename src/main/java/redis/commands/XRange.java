package redis.commands;

import redis.storage.Key;
import redis.storage.Storage;
import redis.storage.StorageException;

import java.util.List;

public final class XRange {

    public static final String NAME = "XRANGE";

    private XRange() {
    }

    public static Effect execute(List<String> args) {
        if (args.size() != 3) {
            return Messages.wrongNumberOfArguments(NAME);
        }

        var key = new Key(args.get(0));
        var start = args.get(1);
        var end = args.get(2);

        try {
            var result = Storage.STORAGE.range(key, start, end);
            return ReadStorageEntries.readEntries(result);
        } catch (StorageException.StreamOperationsException.InvalidId e) {
            return Messages.wrongType("Used wrong ids");
        }
    }
}
