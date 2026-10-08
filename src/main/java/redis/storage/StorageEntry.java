package redis.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

record StorageEntry(long milliseconds, long sequenceNumber, @NonNull List<Tuple> tuples) {

    private static final String SPECIAL_ID = "*";
    private static final Comparator<StorageEntry> KEY_COMPARATOR = Comparator.comparingLong(StorageEntry::milliseconds).thenComparing(StorageEntry::sequenceNumber);

    public static StorageEntry convert(Entry entry, @Nullable StorageEntry lastOne) throws StorageException.StreamOperationsException {
        if (SPECIAL_ID.equals(entry.id())) {
            return convertSpecialCase(entry, lastOne);
        } else {
            return generalCaseConversion(entry, lastOne);
        }
    }

    private static @NonNull StorageEntry generalCaseConversion(Entry entry, @Nullable StorageEntry lastOne) {
        if ("0-0".equals(entry.id())) {
            throw new StorageException.StreamOperationsException.StreamKeyCannotBe00();
        }
        var idParts = entry.id().split("-", -1);
        if (idParts.length != 2) {
            throw new StorageException.StreamOperationsException.InvalidId();
        }
        try {
            var firstPart = idParts[0];
            var secondPart = idParts[1];
            var milliseconds = Long.parseLong(firstPart);
            var sequenceNumber = Long.parseLong(secondPart);
            var storable = new StorageEntry(milliseconds, sequenceNumber, entry.tuples());

            if (lastOne == null) {
                return storable;
            }
            var comparingResult = lastOne.compareKey(storable);
            if (comparingResult > 0) {
                throw new StorageException.StreamOperationsException.StreamKeyMustBeGreaterThanLast();
            } else if (comparingResult < 0) {
                return storable;
            } else {
                throw new StorageException.StreamOperationsException.StreamKeyMustBeGreaterThanLast();
            }
        } catch (NumberFormatException e) {
            throw new StorageException.StreamOperationsException.InvalidId();
        }
    }

    private static @NonNull StorageEntry convertSpecialCase(Entry entry, @Nullable StorageEntry lastOne) {
        long timeStamp = System.currentTimeMillis();
        long sequenceNumber;
        if (lastOne == null) {
            sequenceNumber = 0;
        } else if (lastOne.milliseconds != timeStamp) {
            sequenceNumber = 0;
        } else {
            sequenceNumber = lastOne.sequenceNumber + 1;
        }
        return new StorageEntry(timeStamp, sequenceNumber, entry.tuples());
    }

    public String id() {
        return "%d-%d".formatted(milliseconds, sequenceNumber);
    }

    private int compareKey(StorageEntry other) {
        return KEY_COMPARATOR.compare(this, other);
    }

}
