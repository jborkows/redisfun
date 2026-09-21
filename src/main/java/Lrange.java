import java.util.ArrayList;
import java.util.List;

final class Lrange {
    private Lrange() {
    }

    static List<Effect> execute(List<String> args) {
        if (args.size() != 3) {
            return Messages.wrongNumberOfArguments("Lrange");
        }
        var key = args.getFirst();
        var start = args.get(1);
        var end = args.get(2);
        try {
            var sublist = Storage.STORAGE.sublist(key, Integer.parseInt(start), Integer.parseInt(end));
            var result = new ArrayList<Effect>(sublist.size() + 1);
            result.add(Messages.respLength(sublist.size()));
            for (var s : sublist) {
                result.addAll(Messages.bulkMessage(s));
            }
            return result;

        } catch (NumberFormatException e) {
            return Messages.wrongType("Both start and end have to be numbers");
        }
    }

}
