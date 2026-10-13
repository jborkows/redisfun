package redis.commands;

import redis.storage.Storage;
import redis.storage.StorageException;

import java.util.ArrayList;
import java.util.List;

public final class XRange {

    public static final String NAME = "XRANGE";

    private XRange() {
    }

    public static Effect execute(List<String> args) {
        if (args.size() != 3) {
            return Messages.wrongNumberOfArguments(NAME);
        }

        var key = args.get(0);
        var start = args.get(1);
        var end = args.get(2);

        try {
            var result = Storage.STORAGE.range(key, start, end);

            List<Effect> effect = new ArrayList<>(result.size());
            for (var entry : result) {
                List<Effect> tupleOnes = new ArrayList<>(entry.tuples().size() * 2);
                for (var tuple : entry.tuples()) {
                    tupleOnes.add(Messages.bulkMessage(tuple.key()));
                    tupleOnes.add(Messages.bulkMessage(tuple.value()));
                }
                var singleEntry = Messages.array(Messages.bulkMessage(entry.id()), Messages.array(tupleOnes));
                effect.add(singleEntry);
            }
            return Messages.array(effect);
        } catch (StorageException.StreamOperationsException.InvalidId e) {
            return Messages.wrongType("Used wrong ids");
        }
    }
}
