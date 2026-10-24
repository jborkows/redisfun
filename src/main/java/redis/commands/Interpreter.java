package redis.commands;

import redis.protocol.Command;
import redis.storage.Storage;
import redis.storage.StorageException;
import redis.storage.StorageException.CannotModifyType;
import redis.storage.StorageException.OperationAllowedOnlyForLists;
import redis.storage.StorageException.OperationAllowedOnlyForSingleElement;
import redis.storage.StorageException.StreamOperationsException.OperationAllowedOnlyForStreams;

import java.util.List;
import java.util.function.Function;

public final class Interpreter implements AutoCloseable, Function<Command, Effect> {

    private final TransactionAware transactionAware = new TransactionAware(Interpreter::react);

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

    private static Effect getCommand(List<String> args) {
        if (args.size() != 1) {
            return Messages.wrongNumberOfArguments("get");
        }
        var found = Storage.STORAGE.get(args.getFirst());
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

    private static Effect react(Command command) {
        try {
            return switch (command.commandName()) {
                case "PING" -> Messages.simpleMessage("PONG");
                case "ECHO" -> echo(command.args());
                case "GET" -> getCommand(command.args());
                case Incr.NAME -> Incr.execute(command.args());
                case SetCommand.NAME -> SetCommand.execute(command.args());
                case Rpush.NAME -> Rpush.execute(command.args());
                case Lrange.NAME -> Lrange.execute(command.args());
                case Lpush.NAME -> Lpush.execute(command.args());
                case Llen.NAME -> Llen.execute(command.args());
                case Lpop.NAME -> Lpop.execute(command.args());
                case Blpop.NAME -> Blpop.execute(command.args());
                case Type.NAME -> Type.execute(command.args());
                case XAdd.NAME -> XAdd.execute(command.args());
                case XRange.NAME -> XRange.execute(command.args());
                case XRead.NAME -> XRead.execute(command.args());
                default -> Messages.errorMessage("Unknown command '%s'".formatted(command.commandName()));
            };
        } catch (StorageException storageException) {
            return parseException(command.commandName(), storageException);
        }
    }

    public Effect apply(Command command) {
        return transactionAware.apply(command);
    }

    @Override
    public void close() {
        transactionAware.close();
    }

}
