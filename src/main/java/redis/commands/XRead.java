package redis.commands;

import org.jspecify.annotations.NullMarked;
import redis.storage.Entry;
import redis.storage.Key;
import redis.storage.Storage;
import redis.storage.StorageException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public final class XRead {

    public static final String NAME = "XREAD";

    private XRead() {
    }

    public static Effect execute(List<String> args) {
        if (args.size() < 3) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var operation = args.getFirst().toUpperCase();
        if ("STREAMS".equals(operation)) {
            return readStreams(args);
        } else if ("BLOCK".equals(operation)) {
            return blockingReadStreams(args);
        } else {
            return Messages.errorMessage("Not known operation %s".formatted(operation));
        }
    }

    private static Effect readStreams(List<String> args) {
        try {
            var entries = readInParallel(args, 1, Storage.STORAGE::readStreamFrom);
            return arrayResponse(entries);
        } catch (TimeoutException e) {
            throw new RuntimeException(e); // non-blocking reads never time out
        } catch (CommandFailedException e) {
            return e.effect;
        }
    }

    private static Effect blockingReadStreams(List<String> args) {
        var read = readDuration(args);
        return switch (read) {
            case ReadDuration.Failure(Effect effect) -> effect;

            case ReadDuration.Milliseconds(int milliseconds) -> {
                try {
                    var entries = readInParallel(args, 3, (key, id) -> {
                        var futureCleanup = Storage.STORAGE.blockingReadStreamFrom(key, id);
                        return futureCleanup.work(promise -> switch (milliseconds) {
                            case 0 -> promise.get();
                            default -> promise.get(milliseconds, TimeUnit.MILLISECONDS);
                        });
                    });
                    yield arrayResponse(entries);
                } catch (TimeoutException e) {
                    yield Messages.nilArray();
                } catch (CommandFailedException e) {
                    yield e.effect;
                }
            }
        };
    }

    private static List<StreamEntry> readInParallel(List<String> args, int keyIdsOffset, StreamReader reader) throws TimeoutException {
        var keyIdsPairs = args.subList(keyIdsOffset, args.size());
        if (keyIdsPairs.isEmpty() || keyIdsPairs.size() % 2 == 1) {
            throw new CommandFailedException(Messages.wrongNumberOfArguments(NAME));
        }
        int arraySeparation = keyIdsPairs.size() / 2;
        var entries = new ArrayList<StreamEntry>(arraySeparation);
        try (var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<StreamEntry>>(arraySeparation);
            for (int i = 0; i < arraySeparation; i++) {
                var counter = i;
                futures.add(executorService.submit(() -> {
                    var key = keyIdsPairs.get(counter);
                    var result = reader.read(new Key(key), keyIdsPairs.get(counter + arraySeparation));
                    return new StreamEntry(key, result);
                }));
            }
            for (int i = 0; i < arraySeparation; i++) {
                try {
                    entries.add(futures.get(i).get());
                } catch (ExecutionException e) {
                    futures.forEach(future -> future.cancel(true));
                    var cause = e.getCause();
                    if (cause instanceof StorageException.StreamOperationsException.InvalidId) {
                        throw new CommandFailedException(Messages.wrongType("Used wrong ids"));
                    }
                    if (cause instanceof StorageException se) {
                        throw se;
                    }
                    if (cause instanceof TimeoutException te) {
                        throw te;
                    }
                    throw new RuntimeException(cause == null ? e : cause);
                } catch (InterruptedException e) {
                    futures.forEach(future -> future.cancel(true));
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
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
        List<Entry> read(Key key, String id) throws Exception;
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
