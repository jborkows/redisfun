package redis.commands;

import org.jspecify.annotations.NullMarked;
import redis.storage.Entry;
import redis.storage.FutureWithTimeoutCleanup;
import redis.storage.Storage;
import redis.storage.StorageException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

public final class XRead {

    public static final String NAME = "XREAD";

    private XRead() {
    }

    public static Effect execute(List<String> args) {
        if (args.size() < 3) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var operation = args.getFirst();
        if ("STREAMS".equalsIgnoreCase(operation)) {
            if (args.size() % 2 == 0) {
                return Messages.wrongNumberOfArguments(NAME);
            }
            var keyIdsPairs = args.subList(1, args.size());

            int arraySeparation = keyIdsPairs.size() / 2;
            var entries = new ArrayList<ResultWrapper<List<Entry>>>(arraySeparation);
            try (var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new ArrayList<Future<ResultWrapper<List<Entry>>>>(arraySeparation);
                for (int i = 0; i < arraySeparation; i++) {
                    var counter = i;
                    futures.add(executorService.submit(() -> {
                        var result = Storage.STORAGE.readStreamFrom(keyIdsPairs.get(counter), keyIdsPairs.get(counter + arraySeparation));
                        return new ResultWrapper<>(keyIdsPairs.get(counter), result);
                    }));
                }
                for (int i = 0; i < arraySeparation; i++) {
                    try {
                        entries.add(futures.get(i).get());
                    } catch (ExecutionException e) {
                        futures.forEach(future -> future.cancel(true));
                        if (e.getCause() instanceof StorageException.StreamOperationsException.InvalidId) {
                            return Messages.wrongType("Used wrong ids");
                        }
                        if (e.getCause() instanceof StorageException se) {
                            throw se;
                        }
                        throw new RuntimeException(e.getCause());
                    } catch (InterruptedException e) {
                        futures.forEach(future -> future.cancel(true));
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
            }
            var data = extractDataMessages(entries);
            return Messages.array(data);
        } else if ("BLOCK".equalsIgnoreCase(operation)) {
            if (args.size() % 2 == 1) {
                return Messages.wrongNumberOfArguments(NAME);
            }
            var read = readDuration(args);
            if (read instanceof ReadDuration.Failure(Effect effect)) {
                return effect;
            }
            var milliseconds = switch (read) {
                case ReadDuration.Failure _ -> throw new IllegalStateException("how?");
                case ReadDuration.Milliseconds x -> x.value();
            };


            var keyIdsPairs = args.subList(2, args.size());

            int arraySeparation = keyIdsPairs.size() / 2;
            var entries = new ArrayList<ResultWrapper<List<Entry>>>(arraySeparation);
            try (var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new ArrayList<Future<ResultWrapper<FutureWithTimeoutCleanup<List<Entry>>>>>(arraySeparation);
                for (int i = 0; i < arraySeparation; i++) {
                    var counter = i;
                    futures.add(executorService.submit(() -> {
                        var result = Storage.STORAGE.blockingReadStreamFrom(keyIdsPairs.get(counter), keyIdsPairs.get(counter + arraySeparation));
                        return new ResultWrapper<>(keyIdsPairs.get(counter), result);
                    }));
                }
                for (int i = 0; i < arraySeparation; i++) {
                    try {
                        var aPromise = futures.get(i).get();
                        var value = aPromise.entries.work(promise -> switch (milliseconds) {
                            case 0 -> promise.get();
                            default -> promise.get(milliseconds, TimeUnit.MILLISECONDS);
                        });
                        entries.add(aPromise.map(x -> value));

                    } catch (TimeoutException e) {
                        return Messages.nilArray();
                    } catch (ExecutionException e) {
                        futures.forEach(future -> future.cancel(true));
                        if (e.getCause() instanceof StorageException.StreamOperationsException.InvalidId) {
                            return Messages.wrongType("Used wrong ids");
                        }
                        if (e.getCause() instanceof StorageException se) {
                            throw se;
                        }
                        throw new RuntimeException(e.getCause());
                    } catch (InterruptedException e) {
                        futures.forEach(future -> future.cancel(true));
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
            }
            var data = extractDataMessages(entries);
            return Messages.array(data);
        } else {
            return Messages.errorMessage("Not known operation %s".formatted(operation));
        }
    }

    @NullMarked
    private static List<Effect> extractDataMessages(ArrayList<ResultWrapper<List<Entry>>> entries) {
        return entries.stream()
                .map(i -> {
                    var elements = ReadStorageEntries.readEntries(i.entries);
                    return Messages.array(Messages.bulkMessage(i.key), elements);
                })
                .toList();
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

    private sealed interface ReadDuration {
        record Milliseconds(int value) implements ReadDuration {
        }

        record Failure(Effect effect) implements ReadDuration {
        }
    }

    private record ResultWrapper<T>(String key, T entries) {
        <V> ResultWrapper<V> map(Function<T, V> f) {
            return new ResultWrapper<>(key, f.apply(entries));
        }
    }

    private record FResultWrapper(String key, FutureWithTimeoutCleanup<List<Entry>> entries) {
    }
}
