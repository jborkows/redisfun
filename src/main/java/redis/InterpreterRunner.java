package redis;

import redis.commands.EffectChannel;
import redis.protocol.Command;

public interface InterpreterRunner {
    void process(Command command, EffectChannel effectChannel, ServerKind serverKind);

    default void accept(Command command, EffectChannel effectChannel, ServerKind serverKind) {
        process(command, effectChannel, serverKind);
    }
}
