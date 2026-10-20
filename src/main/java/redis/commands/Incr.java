package redis.commands;

import redis.storage.Storage;
import redis.storage.StorageException;

import java.util.List;

public final class Incr {

    public static final String NAME = "INCR";

    private Incr() {
    }

    public static Effect execute(List<String> args) {
        if (args.size() != 1) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var key = args.getFirst();

        try {
            var value = Storage.STORAGE.increment(key);
            return Messages.respInteger(value);
        } catch (StorageException.OperationAllowedOnlyForIntegers e) {
            return Messages.errorMessage("value is not an integer or out of range");
        }

    }

}
