package redis.protocol;

public record CommandResult(Parser parser, CommandState state) {
}
