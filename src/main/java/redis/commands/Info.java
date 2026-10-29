package redis.commands;

import redis.ServerKind;

import java.util.List;

public final class Info {

    public static final String NAME = "INFO";

    private Info() {
    }

    public static void execute(List<String> args, CommandContext context) {
        if (args.size() > 1) {
            context.channel().accept(Messages.wrongNumberOfArguments(NAME));
            return;
        }
        if (args.size() == 1 && !args.getFirst().equalsIgnoreCase("replication")) {
            context.channel().accept(Messages.bulkMessage(""));
            return;
        }

        var message = switch (context.serverKind()) {
            case ServerKind.Master _ -> "role:master";
            case ServerKind.Slave _ -> "role:slave";
        };
        context.send(Messages.bulkMessage(message));
    }
}
