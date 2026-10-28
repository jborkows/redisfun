package redis.commands;

@FunctionalInterface
public interface Scheduler {
    void schedule(Runnable task, long delayMillis);
}
