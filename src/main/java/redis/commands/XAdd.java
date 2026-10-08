package redis.commands;

import redis.storage.Entry;
import redis.storage.Storage;
import redis.storage.Tuple;

import java.util.ArrayList;
import java.util.List;

public final class XAdd {

    public static final String NAME = "XADD";

    private XAdd() {
    }

    public static List<Effect> execute(List<String> args) {
        if ((args.size() < 2)) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var key = args.getFirst();

        var arguments = args.subList(1, args.size());

        if (arguments.size() == 1) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        if (arguments.size() % 2 == 0) {
            throw new RuntimeException("TODO");
        } else {
            var streamKey = arguments.getFirst();
            var tuples = new ArrayList<Tuple>(arguments.size() / 2);
            var tupplable = arguments.subList(1, arguments.size());
            for (int i = 0; i < tupplable.size() / 2; i++) {
                tuples.add(Tuple.forKey(tupplable.get(i)).withValue(arguments.get(i + 1)));
            }
            var id = Storage.STORAGE.add(key, new Entry(streamKey, tuples));
            return Messages.bulkMessage(id);
        }
    }

}
