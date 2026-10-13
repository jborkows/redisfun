package redis.storage;

import org.jspecify.annotations.NonNull;

import java.util.Comparator;

record StorageEntryKey(long milliseconds, long sequenceNumber) implements Comparable<StorageEntryKey> {

    private static final Comparator<StorageEntryKey> COMPARATOR = Comparator
            .comparingLong(StorageEntryKey::milliseconds)
            .thenComparingLong(StorageEntryKey::sequenceNumber);

    public static StorageEntryKey parse(@NonNull String id) {
        var parts = id.split("-", -1);
        if (parts.length != 2) {
            throw new StorageException.StreamOperationsException.InvalidId();
        }
        try {
            var milliseconds = Long.parseLong(parts[0]);
            var sequenceNumber = Long.parseLong(parts[1]);
            return new StorageEntryKey(milliseconds, sequenceNumber);
        } catch (NumberFormatException e) {
            throw new StorageException.StreamOperationsException.InvalidId();
        }
    }

    public String id() {
        return "%d-%d".formatted(milliseconds, sequenceNumber);
    }

    @Override
    public int compareTo(@NonNull StorageEntryKey other) {
        return COMPARATOR.compare(this, other);
    }
}
