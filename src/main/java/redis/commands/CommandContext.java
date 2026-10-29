package redis.commands;

import redis.storage.Storage;

public record CommandContext(Storage storage, Scheduler scheduler, EffectChannel channel, redis.ServerKind serverKind) {
    void send(Effect effect) {
        channel.accept(effect);
    }
}
