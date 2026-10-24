package redis.protocol;

import java.util.List;
import java.util.Locale;

public record Command(String name, List<String> args) {
    public String commandName() {
        return name.toUpperCase(Locale.ROOT);
    }
}
