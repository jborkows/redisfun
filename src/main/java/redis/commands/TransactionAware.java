package redis.commands;

import redis.protocol.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

class TransactionAware implements Function<Command, Effect> {

    private static final String EXEC_COMMAND = "EXEC";
    private final Function<Command, Effect> processor;
    private boolean transactionInProgress = false;
    private List<Command> commandQueue = new ArrayList<>();

    TransactionAware(Function<Command, Effect> processor) {
        this.processor = processor;
    }

    @Override
    public Effect apply(Command command) {
        if (!transactionInProgress) {
            return switch (command.commandName()) {
                case "MULTI" -> {
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
                    yield Messages.errorMessage("EXEC without MULTI");
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
