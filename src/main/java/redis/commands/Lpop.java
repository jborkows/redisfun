package redis.commands;

import redis.storage.StorageException.OnlyPositiveValues;
import redis.storage.Storage;

import java.util.List;

public final class Lpop {

    public static final String NAME = "LPOP";

    private Lpop() {
    }

    public static List<Effect> execute(List<String> args) {
        if (!(args.size() == 1 || args.size() == 2)) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var key = args.getFirst();
        try {
            var howMany = switch (args.size()) {
                case 2 -> Integer.parseInt(args.get(1));
                default -> 1;
            };
            var element = Storage.STORAGE.pop(key, howMany);
            if (element == null) {
                if (requestingSingleElementPop(args)) {
                    return List.of(Messages.nilMessage());
                } else {
                    return List.of(Messages.nilArray());
                }
            } else if (element.size() == 1 && requestingSingleElementPop(args)) {
                return Messages.bulkMessage(element.getFirst());
            } else {
                return Messages.bulkMessages(element);
            }
        } catch (NumberFormatException | OnlyPositiveValues e) {
            return Messages.wrongType("Optional number argument has to be numeric greater or equal than 1");
        }
    }

    private static boolean requestingSingleElementPop(List<String> args) {
        return args.size() == 1;
    }
}
