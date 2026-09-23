import java.util.List;

final class Rpush {

    static final String NAME = "RPUSH";

    private Rpush() {
    }

    static List<Effect> execute(List<String> args) {
        if (args.size() < 2) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var key = args.getFirst();
        var count = Storage.STORAGE.addElement(key, args.subList(1, args.size()));
        return List.of(Messages.respInteger(count));
    }
}
