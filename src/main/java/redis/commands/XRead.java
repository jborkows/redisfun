package redis.commands;

import org.jspecify.annotations.NullMarked;
import redis.storage.Entry;
import redis.storage.Key;
import redis.storage.StorageException;

import java.util.ArrayList;
import java.util.List;

public final class XRead {

    public static final String NAME = "XREAD";

    private XRead() {
    }

    public static void execute(List<String> args, CommandContext context) {
        if (args.size() < 3) {
            context.channel().accept(Messages.wrongNumberOfArguments(NAME));
            return;
        }
        var operation = args.getFirst().toUpperCase();
        if ("STREAMS".equals(operation)) {
            readStreams(args, context);
        } else if ("BLOCK".equals(operation)) {
            blockingReadStreams(args, context);
        } else {
            context.channel().accept(Messages.errorMessage("Not known operation %s".formatted(operation)));
        }
    }

    private static void readStreams(List<String> args, CommandContext context) {
        try {
            var entries = readSequential(args, 1, context.storage()::readStreamFrom, context);
            context.channel().accept(arrayResponse(entries));
        } catch (CommandFailedException e) {
            context.channel().accept(e.effect);
        }
    }

    private static void blockingReadStreams(List<String> args, CommandContext context) {
        var read = readDuration(args);
        switch (read) {
            case ReadDuration.Failure(Effect effect) -> context.channel().accept(effect);
            case ReadDuration.Milliseconds(int milliseconds) -> {
                var keyIdsPairs = args.subList(3, args.size());
                if (keyIdsPairs.isEmpty() || keyIdsPairs.size() % 2 == 1) {
                    context.channel().accept(Messages.wrongNumberOfArguments(NAME));
                    return;
                }
                int arraySeparation = keyIdsPairs.size() / 2;

                var entries = new ArrayList<StreamEntry>(arraySeparation);
                try {
                    entries.addAll(readSequential(args, 3, context.storage()::readStreamFrom, context));
                } catch (CommandFailedException e) {
                    context.channel().accept(e.effect);
                    return;
                }

                if (entries.stream().anyMatch(e -> !e.entries().isEmpty())) {
                    context.channel().accept(arrayResponse(entries));
                    return;
                }

                var completed = new boolean[]{false};
                for (int i = 0; i < arraySeparation; i++) {
                    var key = keyIdsPairs.get(i);
                    var id = keyIdsPairs.get(i + arraySeparation);
                    var index = i;
                    context.storage().blockingReadStreamFrom(
                            new Key(key),
                            id,
                            result -> {
                                if (completed[0]) {
                                    return;
                                }
                                completed[0] = true;
                                var response = new ArrayList<StreamEntry>(arraySeparation);
                                for (int j = 0; j < arraySeparation; j++) {
                                    if (j == index) {
                                        response.add(new StreamEntry(keyIdsPairs.get(j), result));
                                    } else {
                                        response.add(new StreamEntry(keyIdsPairs.get(j), List.of()));
                                    }
                                }
                                context.channel().accept(arrayResponse(response));
                            },
                            () -> {
                                if (completed[0]) {
                                    return;
                                }
                                completed[0] = true;
                                context.channel().accept(Messages.nilArray());
                            },
                            context.scheduler(),
                            milliseconds
                    );
                }
            }
        }
    }

    private static List<StreamEntry> readSequential(List<String> args, int keyIdsOffset, StreamReader reader, CommandContext context) {
        var keyIdsPairs = args.subList(keyIdsOffset, args.size());
        if (keyIdsPairs.isEmpty() || keyIdsPairs.size() % 2 == 1) {
            throw new CommandFailedException(Messages.wrongNumberOfArguments(NAME));
        }
        int arraySeparation = keyIdsPairs.size() / 2;
        var entries = new ArrayList<StreamEntry>(arraySeparation);
        for (int i = 0; i < arraySeparation; i++) {
            var key = keyIdsPairs.get(i);
            try {
                var result = reader.read(new Key(key), keyIdsPairs.get(i + arraySeparation));
                entries.add(new StreamEntry(key, result));
            } catch (StorageException.StreamOperationsException.InvalidId e) {
                throw new CommandFailedException(Messages.wrongType("Used wrong ids"));
            }
        }
        return entries;
    }

    @NullMarked
    private static Effect arrayResponse(List<StreamEntry> entries) {
        var data = entries.stream()
                .map(i -> {
                    var elements = ReadStorageEntries.readEntries(i.entries());
                    return Messages.array(Messages.bulkMessage(i.key()), elements);
                })
                .toList();
        return Messages.array(data);
    }

    private static ReadDuration readDuration(List<String> args) {
        try {
            int milliseconds = Integer.parseInt(args.get(1));
            if (milliseconds < 0) {
                return new ReadDuration.Failure(Messages.wrongType("timeout should be 0 or positive."));
            } else if (milliseconds > 60 * 60 * 1000) {
                return new ReadDuration.Failure(Messages.wrongType("timeout too big"));
            }
            return new ReadDuration.Milliseconds(milliseconds);
        } catch (NumberFormatException e) {
            return new ReadDuration.Failure(Messages.wrongType("timeout should be 0 or positive"));
        }
    }

    @FunctionalInterface
    private interface StreamReader {
        List<Entry> read(Key key, String id);
    }

    private sealed interface ReadDuration {
        record Milliseconds(int value) implements ReadDuration {
        }

        record Failure(Effect effect) implements ReadDuration {
        }
    }

    private static final class CommandFailedException extends RuntimeException {
        private final Effect effect;

        CommandFailedException(Effect effect) {
            this.effect = effect;
        }
    }

    private record StreamEntry(String key, List<Entry> entries) {
    }
}
