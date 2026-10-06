package redis.commands;

public sealed interface Effect {

    record PrintText(String text) implements Effect {
    }
}
