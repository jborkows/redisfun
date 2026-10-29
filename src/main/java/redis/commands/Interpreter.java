package redis.commands;

import redis.ServerKind;
import redis.protocol.Command;
import redis.storage.Key;
import redis.storage.Storage;
import redis.storage.StorageException;
import redis.storage.StorageException.CannotModifyType;
import redis.storage.StorageException.OperationAllowedOnlyForLists;
import redis.storage.StorageException.OperationAllowedOnlyForSingleElement;
import redis.storage.StorageException.StreamOperationsException.OperationAllowedOnlyForStreams;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Interpreter implements AutoCloseable {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService schedulerExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Storage storage = new Storage();
    private final Scheduler scheduler = (task, delayMillis) -> schedulerExecutor.schedule(
            () -> executor.submit(task), delayMillis, TimeUnit.MILLISECONDS
    );
    private final TransactionAware transactionAware = new TransactionAware(storage, this::react);

    private static Effect parseException(String commandName, StorageException storageException) {
        return switch (storageException) {
            case OperationAllowedOnlyForLists _ ->
                    Messages.wrongType("Operation %s allowed only for lists".formatted(commandName));
            case OperationAllowedOnlyForStreams _ ->
                    Messages.wrongType("Operation %s allowed only for streams".formatted(commandName));
            case OperationAllowedOnlyForSingleElement _ ->
                    Messages.wrongType("Operation %s allowed only for single element".formatted(commandName));
            case CannotModifyType cannotModifyType ->
                    Messages.wrongType("Operation %s cannot change type from %s".formatted(commandName, cannotModifyType.existingType));
            default -> Messages.errorMessage("Unexpected exception: " + storageException);
        };
    }

    private static Effect getCommand(List<String> args, CommandContext context) {
        if (args.size() != 1) {
            return Messages.wrongNumberOfArguments("get");
        }
        var found = context.storage().get(new Key(args.getFirst()));
        if (found == null) {
            return Messages.nilMessage();
        } else {
            return Messages.bulkMessage(found);
        }
    }

    private static Effect echo(List<String> args) {
        if (args.size() != 1) {
            return Messages.wrongNumberOfArguments("echo");
        }
        var value = args.getFirst();
        return Messages.bulkMessage(value);
    }

    private void react(Command command, EffectChannel channel, ServerKind serverKind) {
        var context = new CommandContext(storage, scheduler, channel, serverKind);
        try {
            switch (command.commandName()) {
                case "PING" -> channel.accept(Messages.simpleMessage("PONG"));
                case "ECHO" -> channel.accept(echo(command.args()));
                case "GET" -> channel.accept(getCommand(command.args(), context));
                case Incr.NAME -> Incr.execute(command.args(), context);
                case SetCommand.NAME -> SetCommand.execute(command.args(), context);
                case Rpush.NAME -> Rpush.execute(command.args(), context);
                case Lrange.NAME -> Lrange.execute(command.args(), context);
                case Lpush.NAME -> Lpush.execute(command.args(), context);
                case Llen.NAME -> Llen.execute(command.args(), context);
                case Lpop.NAME -> Lpop.execute(command.args(), context);
                case Blpop.NAME -> Blpop.execute(command.args(), context);
                case Type.NAME -> Type.execute(command.args(), context);
                case XAdd.NAME -> XAdd.execute(command.args(), context);
                case XRange.NAME -> XRange.execute(command.args(), context);
                case XRead.NAME -> XRead.execute(command.args(), context);
                case Info.NAME -> Info.execute(command.args(), context);
                default ->
                        channel.accept(Messages.errorMessage("Unknown command '%s'".formatted(command.commandName())));
            }
        } catch (StorageException storageException) {
            channel.accept(parseException(command.commandName(), storageException));
        }
    }

    public void apply(Command command, EffectChannel consumer, ServerKind serverKind) {
        executor.submit(() -> transactionAware.apply(command, consumer, serverKind));
    }

    public void onConnectionClosed(EffectChannel channel) {
        executor.submit(() -> {
            transactionAware.closeConnection(channel);
            storage.closeConnection(channel);
        });
    }

    @Override
    public void close() {
        transactionAware.close();
        schedulerExecutor.shutdown();
        executor.shutdown();
    }

}
