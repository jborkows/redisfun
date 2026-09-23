import java.util.List;

final class Llen {

    static final String NAME = "LLEN";

    private Llen() {
    }

    static List<Effect> execute(List<String> args) {
        if (args.size() != 1) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var key = args.getFirst();
        int count = Storage.STORAGE.lengthOf(key);
        return List.of(Messages.respInteger(count));
    }

}
