package redis.storage;

public sealed abstract class StorageException extends RuntimeException {

    public static final class OperationAllowedOnlyForSingleElement extends StorageException {

    }

    public static final class OperationAllowedOnlyForLists extends StorageException {

    }

    public static final class OperationAllowedOnlyForStreams extends StorageException {

    }

    public static final class OnlyPositiveValues extends StorageException {
    }

    public static final class CannotModifyType extends StorageException {
        public final String existingType;

        CannotModifyType(String existingType) {
            this.existingType = existingType;
        }

        static CannotModifyType cannotModifyFromElement() {
            return new CannotModifyType("Element");
        }

        static CannotModifyType cannotModifyFromList() {
            return new CannotModifyType("List");
        }

    }
}
