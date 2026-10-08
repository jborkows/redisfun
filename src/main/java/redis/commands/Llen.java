package redis.commands;

import redis.storage.Storage;

import java.util.List;

public final class Llen {

    public static final String NAME = "LLEN";

    private Llen() {
    }

    public static Effect execute(List<String> args) {
        if (args.size() != 1) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var key = args.getFirst();
        int count = Storage.STORAGE.lengthOf(key);
        return Messages.respInteger(count);
    }

}
