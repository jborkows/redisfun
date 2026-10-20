package redis.storage;

public sealed abstract class StorageException extends RuntimeException {

    public static final class OperationAllowedOnlyForSingleElement extends StorageException {

    }

    public static final class OperationAllowedOnlyForLists extends StorageException {

    }

    public static final class OperationAllowedOnlyForIntegers extends StorageException {
    }


    public static sealed abstract class StreamOperationsException extends StorageException {

        public static final class OperationAllowedOnlyForStreams extends StreamOperationsException {
        }

        public static final class StreamKeyMustBeGreaterThanLast extends StreamOperationsException {
        }

        public static final class InvalidId extends StreamOperationsException {
        }

        public static final class StreamKeyCannotBe00 extends StreamOperationsException {
        }
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
