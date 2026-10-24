package redis.commands;

import redis.protocol.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

class TransactionAware implements Function<Command, Effect> {

    private static final String EXEC_COMMAND = "EXEC";
    private static final String MULTI_COMMAND = "MULTI";
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
                    yield Messages.simpleMessage("OK");
                }
                case EXEC_COMMAND -> {
                    if (!command.args().isEmpty()) {
                        yield Messages.wrongNumberOfArguments(command.name());
                    }
                    yield Messages.errorMessage("%s without %s".formatted(EXEC_COMMAND, MULTI_COMMAND));
                }
                default -> processor.apply(command);
            };
        } else {
            return switch (command.commandName()) {
                case EXEC_COMMAND -> {
                    var messages = commandQueue.stream().map(processor).toList();
                    transactionInProgress = false;
                    yield Messages.array(messages);
                }
                default -> {
                    commandQueue.add(command);
                    yield Messages.simpleMessage("QUEUED");
                }
            };
        }
    }


    void close() {
        commandQueue.clear();
    }
}
