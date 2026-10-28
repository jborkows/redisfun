package redis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class CliParser {

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
        });

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
