package redis.commands;

import redis.storage.Entry;
import redis.storage.Storage;
import redis.storage.StorageException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class XRead {

    public static final String NAME = "XREAD";

    private XRead() {
    }

    public static Effect execute(List<String> args) {
        if (args.size() < 3 || args.size() % 2 == 0) {
            return Messages.wrongNumberOfArguments(NAME);
        }
        var operation = args.getFirst();
        if (!"STREAMS".equalsIgnoreCase(operation)) {
            return Messages.errorMessage("Not known operation %s".formatted(operation));
        }
        var keyIdsPairs = args.subList(1, args.size());

        int arraySeparation = keyIdsPairs.size() / 2;
        var entries = new ArrayList<ResultWrapper>(arraySeparation);
        try (var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<ResultWrapper>>(arraySeparation);
            for (int i = 0; i < arraySeparation; i++) {
                var counter = i;
                futures.add(executorService.submit(() -> {
                    var result = Storage.STORAGE.readStreamFrom(keyIdsPairs.get(counter), keyIdsPairs.get(counter + arraySeparation));
                    return new ResultWrapper(keyIdsPairs.get(counter), result);
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
        var data = entries.stream()
                .map(i -> {
                    var elements = ReadStorageEntries.readEntries(i.entries);
                    return Messages.array(Messages.bulkMessage(i.key), elements);
                })
                .toList();
        return Messages.array(data);
    }

    private record ResultWrapper(String key, List<Entry> entries) {
    }
}
