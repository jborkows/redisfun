package redis.commands;

import redis.storage.Entry;
import redis.storage.Key;
import redis.storage.StorageException;
import redis.storage.Tuple;

import java.util.ArrayList;
import java.util.List;

public final class XAdd {

    public static final String NAME = "XADD";

    private XAdd() {
    }

    public static void execute(List<String> args, CommandContext context) {
        if ((args.size() < 2)) {
            context.channel().accept(Messages.wrongNumberOfArguments(NAME));
            return;
        }
        var key = new Key(args.getFirst());

        var arguments = args.subList(1, args.size());

        if (arguments.size() == 1) {
            context.channel().accept(Messages.wrongNumberOfArguments(NAME));
            return;
        }
        if (arguments.size() % 2 == 0) {
            context.channel().accept(Messages.wrongNumberOfArguments(NAME));
        } else {
            var entryId = arguments.getFirst();
            var tuples = new ArrayList<Tuple>(arguments.size() / 2);
            var tupplable = arguments.subList(1, arguments.size());
            for (int i = 0; i < tupplable.size(); i += 2) {
                tuples.add(Tuple.forKey(tupplable.get(i)).withValue(tupplable.get(i + 1)));
            }
            try {
                var id = context.storage().add(key, new Entry(entryId, tuples));
                context.channel().accept(Messages.bulkMessage(id.value()));
            } catch (StorageException.StreamOperationsException.StreamKeyCannotBe00 _) {
                context.channel().accept(Messages.errorMessage("The ID specified in XADD must be greater than 0-0"));
            } catch (StorageException.StreamOperationsException.StreamKeyMustBeGreaterThanLast |
                     StorageException.StreamOperationsException.InvalidId _) {
                context.channel().accept(Messages.errorMessage("The ID specified in XADD is equal or smaller than the target stream top item"));
            }
        }
    }

}
