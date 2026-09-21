import java.util.List;

final class Rpush {
    private Rpush() {
    }

    static List<Effect> execute(List<String> args) {
        if (args.size() < 2) {
            return Messages.wrongNumberOfArguments("rpush");
        }
        var key = args.getFirst();
        var count = Storage.STORAGE.addElement(key, args.subList(1, args.size()));
        return List.of(Messages.respInteger(count));
    }
}
