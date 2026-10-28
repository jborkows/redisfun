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

    private final Storage storage;
    private final BiConsumer<Command, EffectChannel> processor;
    private final Map<EffectChannel, ConnectionState> connections = new HashMap<>();

    TransactionAware(Storage storage, BiConsumer<Command, EffectChannel> processor) {
        this.storage = storage;
        this.processor = processor;
    }

    void apply(Command command, EffectChannel consumer) {
        var state = connections.computeIfAbsent(consumer, _ -> new ConnectionState());
        if (!state.transactionInProgress) {
            switch (command.commandName()) {
                case MULTI_COMMAND -> {
                    if (!command.args().isEmpty()) {
                        consumer.accept(Messages.wrongNumberOfArguments(command.name()));
                    } else {
                        state.transactionInProgress = true;
                        state.commandQueue.clear();
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
                            Watcher watcher = key -> state.watched.computeIfPresent(key, (_, _) -> ChangeObserved.CHANGED);
                            var key = new Key(commandArg);
                            state.watchers.put(key, watcher);
                            state.watched.put(key, ChangeObserved.NO_CHANGE);
                            storage.registerWatcher(key, watcher);
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
                    } else if (ChangeObserved.CHANGED == transactionPreparations(state)) {
                        state.transactionInProgress = false;
                        consumer.accept(Messages.nilArray());
                    } else {
                        var messages = new ArrayList<Effect>();
                        for (var queuedCommand : state.commandQueue) {
                            processor.accept(queuedCommand, messages::add);
                        }
                        state.transactionInProgress = false;
                        consumer.accept(Messages.array(messages));
                    }
                }
                case DISCARD_COMMAND -> {
                    if (!command.args().isEmpty()) {
                        consumer.accept(Messages.wrongNumberOfArguments(command.name()));
                    } else {
                        state.commandQueue.clear();
                        state.transactionInProgress = false;
                        consumer.accept(Messages.ok());
                    }
                }
                default -> {
                    state.commandQueue.add(command);
                    consumer.accept(Messages.simpleMessage("QUEUED"));
                }
            }
        }
    }

    private ChangeObserved transactionPreparations(ConnectionState state) {
        state.watchers.forEach(storage::deregisterWatcher);
        var changed = state.watched.entrySet().stream().filter(entry -> entry.getValue() == ChangeObserved.CHANGED).findAny().map(Map.Entry::getValue).orElse(ChangeObserved.NO_CHANGE);
        state.watched.clear();
        state.watchers.clear();
        return changed;
    }

    void close() {
        connections.values().forEach(state -> {
            state.commandQueue.clear();
            state.watchers.forEach(storage::deregisterWatcher);
            state.watchers.clear();
        });
        connections.clear();
    }

    private static final class ConnectionState {
        private final Map<Key, Watcher> watchers = new HashMap<>();
        private final Map<Key, ChangeObserved> watched = new HashMap<>();
        private final List<Command> commandQueue = new ArrayList<>();
        private boolean transactionInProgress = false;
    }

    private enum ChangeObserved {
        NO_CHANGE, CHANGED
    }
}
