package redis.commands;

import redis.storage.Storage;

import java.util.List;

public final class Rpush {

    public static final String NAME = "RPUSH";

    private Rpush() {
    }

    public static List<Effect> execute(List<String> args) {
        if (args.size() < 2) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var key = args.getFirst();
        var count = Storage.STORAGE.addElement(key, args.subList(1, args.size()));
        return List.of(Messages.respInteger(count));
    }
}
