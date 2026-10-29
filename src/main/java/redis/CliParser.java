package redis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class CliParser {
    private final static Pattern SPACE_PATTERN = Pattern.compile("\\s+");

    private CliParser() {
    }

    static List<RedisServer.ConfigChanger> parse(String[] args) {
        var configs = new ArrayList<RedisServer.ConfigChanger>();
        for (int index = 0; index < args.length; index++) {
            var potentialOption = args[index];
            var option = Option.parse(potentialOption);
            if (option == null) {
                throw new IllegalArgumentException("Invalid option: " + potentialOption);
            }
            configs.add(option.changer.apply(args, index + 1));
            index += option.howManyArguments;
        }
        return configs;
    }

    enum Option {
        PORT(1, "--port", (args, offset) -> {
            if (args.length <= offset) {
                throw new IllegalArgumentException("Missing port number");
            }
            var port = args[offset];
            try {
                return RedisServer.portChanger(Integer.parseInt(port));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port number");
            }
        }),
        REPLICA_FOR(1, "--replicaof", ((args, offset) -> {
            if (args.length <= offset) {
                throw new IllegalArgumentException("Missing replica info");
            }
            var location = args[offset];
            try {
                var parts = SPACE_PATTERN.split(location, -1);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid replica info");
                }
                return RedisServer.replicaOf(parts[0], Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port number");
            }
        }));

        private static final Map<String, Option> options = Arrays.stream(Option.values())
                .collect(Collectors.toUnmodifiableMap(i -> i.option, i -> i));

        final int howManyArguments;
        final ArgumentParser changer;
        private final String option;

        Option(int howManyArguments, String option, ArgumentParser changer) {
            this.howManyArguments = howManyArguments;
            this.option = option;
            this.changer = changer;
        }

        static Option parse(String option) {
            return options.get(option);
        }
    }

    interface ArgumentParser {
        RedisServer.ConfigChanger apply(String[] args, int offset);
    }
}
