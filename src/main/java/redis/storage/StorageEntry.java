package redis.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

record StorageEntry(@NonNull StorageEntryKey key, @NonNull List<Tuple> tuples) {

    private static final String SPECIAL_ID = "*";

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
        try {
            var storable = convertParts(entry, lastOne);
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

    private static @NonNull StorageEntry convertParts(@NonNull Entry entry, @Nullable StorageEntry lastOne) {
        var idParts = entry.id().split("-", -1);
        if (idParts.length != 2) {
            throw new StorageException.StreamOperationsException.InvalidId();
        }
        var firstPart = idParts[0];
        var secondPart = idParts[1];
        var milliseconds = Long.parseLong(firstPart);
        long sequenceNumber;
        if (SPECIAL_ID.equals(secondPart)) {
            if (lastOne == null) {
                if (milliseconds == 0) {
                    sequenceNumber = 1;
                } else {
                    sequenceNumber = 0;
                }
            } else {
                if (lastOne.key.milliseconds() == milliseconds) {
                    sequenceNumber = lastOne.key.sequenceNumber() + 1;
                } else {
                    sequenceNumber = milliseconds == 0 ? 1 : 0;
                }
            }
        } else {
            sequenceNumber = Long.parseLong(secondPart);
        }
        return new StorageEntry(new StorageEntryKey(milliseconds, sequenceNumber), entry.tuples());
    }

    private static @NonNull StorageEntry convertSpecialCase(Entry entry, @Nullable StorageEntry lastOne) {
        long timeStamp = System.currentTimeMillis();
        long sequenceNumber;
        if (lastOne == null) {
            sequenceNumber = 0;
        } else if (lastOne.key.milliseconds() != timeStamp) {
            sequenceNumber = 0;
        } else {
            sequenceNumber = lastOne.key.sequenceNumber() + 1;
        }
        return new StorageEntry(new StorageEntryKey(timeStamp, sequenceNumber), entry.tuples());
    }

    public String id() {
        return key.id();
    }

    private int compareKey(StorageEntry other) {
        return key.compareTo(other.key);
    }

}
