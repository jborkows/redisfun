package redis.commands;

import redis.storage.Storage;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

public final class SetCommand {

    public static final String NAME = "SET";
    static final String EX = "EX";
    static final String PX = "PX";

    private SetCommand() {
    }

    public static Effect execute(List<String> args) {
        if (args.size() < 2) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var options = args.subList(2, args.size());
        if (hasInvalidOptions(options)) {
            return Messages.syntaxError();
        }
        var key = args.get(0);
        var value = args.get(1);
        var expiry = expiryDuration(options);
        if (expiry != null) {
            Storage.STORAGE.putExpirable(key, value, expiry);
        } else {
            Storage.STORAGE.put(key, value);
        }
        return Messages.simpleMessage("OK");
    }

    private static @Nullable Duration expiryDuration(List<String> options) {
        for (int i = 0; i < options.size(); i += 2) {
            var option = options.get(i).toUpperCase(Locale.ROOT);
            var time = Long.parseLong(options.get(i + 1));
            if (option.equals(EX)) {
                return Duration.ofSeconds(time);
            }
            if (option.equals(PX)) {
                return Duration.ofMillis(time);
            }
        }
        return null;
    }

    private static boolean hasInvalidOptions(List<String> options) {
        boolean seenExpiry = false;
        for (int i = 0; i < options.size(); i += 2) {
            var option = options.get(i).toUpperCase(Locale.ROOT);
            if (!option.equals(EX) && !option.equals(PX)) {
                return true;
            }
            if (i + 1 >= options.size()) {
                return true; // EX/PX without a time value
            }
            if (seenExpiry) {
                return true; // EX and PX (or the same option twice) in one command
            }
            seenExpiry = true;
            try {
                if (Long.parseLong(options.get(i + 1)) <= 0) {
                    return true;
                }
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return false;
    }
}
