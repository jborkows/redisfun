package redis.commands;

import redis.protocol.Command;
import redis.storage.Key;
import redis.storage.Storage;
import redis.storage.Watcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

class TransactionAware {
    private static final String EXEC_COMMAND = "EXEC";
    private static final String MULTI_COMMAND = "MULTI";
    private static final String DISCARD_COMMAND = "DISCARD";
    private static final String WATCH_COMMAND = "WATCH";
    private final Map<Key, Watcher> watchers = new HashMap<>();
    private final Map<Key, ChangeObserved> watched = new HashMap<>();

    private final BiConsumer<Command, EffectChannel> processor;
    private final List<Command> commandQueue = new ArrayList<>();
    private boolean transactionInProgress = false;

    TransactionAware(BiConsumer<Command, EffectChannel> processor) {
        this.processor = processor;
    }

    void apply(Command command, EffectChannel consumer) {
        if (!transactionInProgress) {
            switch (command.commandName()) {
                case MULTI_COMMAND -> {
                    if (!command.args().isEmpty()) {
                        consumer.accept(Messages.wrongNumberOfArguments(command.name()));
                    } else {
                        transactionInProgress = true;
                        commandQueue.clear();
                        consumer.accept(Messages.ok());
                    }
                }
                case EXEC_COMMAND -> {
                    if (!command.args().isEmpty()) {
                        consumer.accept(Messages.wrongNumberOfArguments(command.name()));
                    } else {
                        consumer.accept(Messages.errorMessage("%s without %s".formatted(EXEC_COMMAND, MULTI_COMMAND)));
                    }
                }
                case DISCARD_COMMAND -> {
                    if (!command.args().isEmpty()) {
                        consumer.accept(Messages.wrongNumberOfArguments(command.name()));
                    } else {
                        consumer.accept(Messages.errorMessage("%s without %s".formatted(DISCARD_COMMAND, MULTI_COMMAND)));
                    }
                }
                case WATCH_COMMAND -> {
                    if (command.args().isEmpty()) {
                        consumer.accept(Messages.wrongNumberOfArguments(command.name()));
                    } else {
                        command.args().forEach(commandArg -> {
                            Watcher watcher = key -> watched.computeIfPresent(key, (_, _) -> ChangeObserved.CHANGED);
                            var key = new Key(commandArg);
                            watchers.put(key, watcher);
                            watched.put(key, ChangeObserved.NO_CHANGE);
                            Storage.STORAGE.registerWatcher(key, watcher);
                        });
                        consumer.accept(Messages.ok());
                    }
                }
                default -> processor.accept(command, consumer);
            }
        } else {
            switch (command.commandName()) {
                case WATCH_COMMAND -> {
                    if (command.args().isEmpty()) {
                        consumer.accept(Messages.wrongNumberOfArguments(command.name()));
                    } else {
                        consumer.accept(Messages.errorMessage("%s inside %s is not allowed".formatted(WATCH_COMMAND, MULTI_COMMAND)));
                    }
                }
                case EXEC_COMMAND -> {
                    if (!command.args().isEmpty()) {
                        consumer.accept(Messages.wrongNumberOfArguments(command.name()));
                    } else if (ChangeObserved.CHANGED == transactionPreparations()) {
                        transactionInProgress = false;
                        consumer.accept(Messages.nilArray());
                    } else {
                        //Yes I know is not atomic, if I would start from scratch probably I would use actor model like behavior
                        var messages = new ArrayList<Effect>();
                        for (var queuedCommand : commandQueue) {
                            processor.accept(queuedCommand, messages::add);
                        }
                        transactionInProgress = false;
                        consumer.accept(Messages.array(messages));
                    }
                }
                case DISCARD_COMMAND -> {
                    if (!command.args().isEmpty()) {
                        consumer.accept(Messages.wrongNumberOfArguments(command.name()));
                    } else {
                        commandQueue.clear();
                        transactionInProgress = false;
                        consumer.accept(Messages.ok());
                    }
                }
                default -> {
                    commandQueue.add(command);
                    consumer.accept(Messages.simpleMessage("QUEUED"));
                }
            }
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
        watchers.forEach(Storage.STORAGE::deregisterWatcher);
        watchers.clear();
    }

    private enum ChangeObserved {
        NO_CHANGE, CHANGED
    }
}
