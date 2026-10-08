package redis.storage;

public record Tuple(String key, String value) {
    public static ForKey forKey(String key) {
        return new ForKey(key);
    }

    public record ForKey(String key) {
        public Tuple withValue(String value) {
            return new Tuple(key, value);
        }
    }
}
