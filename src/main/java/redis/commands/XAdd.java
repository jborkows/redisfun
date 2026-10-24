package redis.commands;

import redis.storage.Entry;
import redis.storage.Storage;
import redis.storage.StorageException;
import redis.storage.Tuple;

import java.util.ArrayList;
import java.util.List;

public final class XAdd {

    public static final String NAME = "XADD";

    private XAdd() {
    }

    public static Effect execute(List<String> args) {
        if ((args.size() < 2)) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var key = args.getFirst();

        var arguments = args.subList(1, args.size());

        if (arguments.size() == 1) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        if (arguments.size() % 2 == 0) {
            return Messages.wrongNumberOfArguments(NAME);
        } else {
            var entryId = arguments.getFirst();
            var tuples = new ArrayList<Tuple>(arguments.size() / 2);
            var tupplable = arguments.subList(1, arguments.size());
            for (int i = 0; i < tupplable.size(); i += 2) {
                tuples.add(Tuple.forKey(tupplable.get(i)).withValue(tupplable.get(i + 1)));
            }
            try {
                var id = Storage.STORAGE.add(key, new Entry(entryId, tuples));
                return Messages.bulkMessage(id.value());
            } catch (StorageException.StreamOperationsException.StreamKeyCannotBe00 _) {
                return Messages.errorMessage("The ID specified in XADD must be greater than 0-0");
            } catch (StorageException.StreamOperationsException.StreamKeyMustBeGreaterThanLast |
                     StorageException.StreamOperationsException.InvalidId _) {
                return Messages.errorMessage("The ID specified in XADD is equal or smaller than the target stream top item");
            }
        }
    }

}
