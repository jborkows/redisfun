package redis.storage;

public interface Watcher {
    void onValueChanged(Key key);
}
