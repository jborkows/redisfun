package redis.commands;

import redis.storage.Key;
import redis.storage.Storage;

import java.util.List;

public final class Lrange {

    public static final String NAME = "LRANGE";

    private Lrange() {
    }

    public static Effect execute(List<String> args) {
        if (args.size() != 3) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var key = new Key(args.getFirst());
        var start = args.get(1);
        var end = args.get(2);
        try {
            var sublist = Storage.STORAGE.sublist(key, Integer.parseInt(start), Integer.parseInt(end));
            return Messages.bulkMessages(sublist);
        } catch (NumberFormatException e) {
            return Messages.wrongType("Both start and end have to be numbers");
        }
    }

}
