import java.util.List;

final class Lpop {

    static final String NAME = "LPOP";

    private Lpop() {
    }

    static List<Effect> execute(List<String> args) {
        if (args.size() != 1) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var key = args.getFirst();
        var element = Storage.STORAGE.pop(key);
        if (element == null) {
            return List.of(Messages.nilMessage());
        } else {
            return Messages.bulkMessage(element);
        }
    }

}
