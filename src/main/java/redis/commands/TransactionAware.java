package redis.commands;

import redis.protocol.Command;
import redis.storage.Key;
import redis.storage.Storage;
import redis.storage.Watcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

class TransactionAware implements Function<Command, Effect> {

    private static final String EXEC_COMMAND = "EXEC";
    private static final String MULTI_COMMAND = "MULTI";
    private static final String DISCARD_COMMAND = "DISCARD";
    private static final String WATCH_COMMAND = "WATCH";
    private final Map<Key, Watcher> watchers = new HashMap<>();
    private final Map<Key, ChangeObserved> watched = new HashMap<>();

    private final Function<Command, Effect> processor;
    private final List<Command> commandQueue = new ArrayList<>();
    private boolean transactionInProgress = false;

    TransactionAware(Function<Command, Effect> processor) {
        this.processor = processor;
    }

    @Override
    public Effect apply(Command command) {
        if (!transactionInProgress) {
            return switch (command.commandName()) {
                case MULTI_COMMAND -> {
                    if (!command.args().isEmpty()) {
                        yield Messages.wrongNumberOfArguments(command.name());
                    }
                    transactionInProgress = true;
                    commandQueue.clear();
                    yield Messages.ok();
                }
                case EXEC_COMMAND -> {
                    if (!command.args().isEmpty()) {
                        yield Messages.wrongNumberOfArguments(command.name());
                    }
                    yield Messages.errorMessage("%s without %s".formatted(EXEC_COMMAND, MULTI_COMMAND));
                }
                case DISCARD_COMMAND -> {
                    if (!command.args().isEmpty()) {
                        yield Messages.wrongNumberOfArguments(command.name());
                    }
                    yield Messages.errorMessage("%s without %s".formatted(DISCARD_COMMAND, MULTI_COMMAND));
                }
                case WATCH_COMMAND -> {
                    if (command.args().isEmpty()) {
                        yield Messages.wrongNumberOfArguments(command.name());
                    }
                    command.args().forEach(commandArg -> {
                        Watcher watcher = key -> watched.computeIfPresent(key, (_, _) -> ChangeObserved.CHANGED);
                        var key = new Key(commandArg);
                        watchers.put(key, watcher);
                        watched.put(key, ChangeObserved.NO_CHANGE);
                        Storage.STORAGE.registerWatcher(key, watcher);
                    });
                    yield Messages.ok();

                }
                default -> processor.apply(command);
            };
        } else {
            return switch (command.commandName()) {
                case WATCH_COMMAND -> {
                    if (command.args().isEmpty()) {
                        yield Messages.wrongNumberOfArguments(command.name());
                    }
                    yield Messages.errorMessage("%s inside %s is not allowed".formatted(WATCH_COMMAND, MULTI_COMMAND));
                }
                case EXEC_COMMAND -> {
                    if (!command.args().isEmpty()) {
                        yield Messages.wrongNumberOfArguments(command.name());
                    }
                    if (ChangeObserved.CHANGED == transactionPreparations()) {
                        transactionInProgress = false;
                        yield Messages.nilArray();
                    }

                    var messages = commandQueue.stream().map(processor).toList();
                    transactionInProgress = false;
                    yield Messages.array(messages);
                }
                case DISCARD_COMMAND -> {
                    if (!command.args().isEmpty()) {
                        yield Messages.wrongNumberOfArguments(command.name());
                    }
                    commandQueue.clear();
                    transactionInProgress = false;
                    yield Messages.ok();
                }
                default -> {
                    commandQueue.add(command);
                    yield Messages.simpleMessage("QUEUED");
                }
            };
        }
    }

    private ChangeObserved transactionPreparations() {
        watchers.forEach(Storage.STORAGE::deregisterWatcher);
        var changed = watched.entrySet().stream().filter(entry -> entry.getValue() == ChangeObserved.CHANGED).findAny().map(Map.Entry::getValue).orElse(ChangeObserved.NO_CHANGE);
        watched.clear();
        watchers.clear();
        return changed;
    }

    void close() {
        commandQueue.clear();
    }

    private enum ChangeObserved {
        NO_CHANGE, CHANGED
    }
}
