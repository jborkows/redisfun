import java.util.List;

public class Rpush {

    static List<Effect> execute(List<String> args) {
        if (args.size() < 2) {
            return Messages.wrongNumberOfArguments("rpush");
        }
        var key = args.get(0);
        Storage.STORAGE.addElement(key, args.subList(1, args.size()));
        var count = Storage.STORAGE.count(key);
        return List.of(Messages.respInteger(count));
    }
}
