package redis.commands;

@FunctionalInterface
public interface EffectChannel {
    void accept(Effect effect);
}
