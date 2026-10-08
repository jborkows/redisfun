package redis.commands;

import java.util.List;

public sealed interface Effect {

    String text();

    record SimpleString(String value) implements Effect {
        @Override
        public String text() {
            return "+" + value + "\r\n";
        }
    }

    record Error(String message) implements Effect {
        @Override
        public String text() {
            return "-ERR " + message + "\r\n";
        }
    }

    record WrongType(String message) implements Effect {
        @Override
        public String text() {
            return "-WRONGTYPE " + message + "\r\n";
        }
    }

    record Integer(long value) implements Effect {
        @Override
        public String text() {
            return ":" + value + "\r\n";
        }
    }

    record BulkString(String value) implements Effect {
        @Override
        public String text() {
            return "$" + value.length() + "\r\n" + value + "\r\n";
        }
    }

    record NullBulk() implements Effect {
        @Override
        public String text() {
            return "$-1\r\n";
        }
    }

    record NullArray() implements Effect {
        @Override
        public String text() {
            return "*-1\r\n";
        }
    }

    record Array(List<Effect> elements) implements Effect {
        public Array {
            elements = List.copyOf(elements);
        }

        @Override
        public String text() {
            var builder = new StringBuilder();
            builder.append("*").append(elements.size()).append("\r\n");
            for (var element : elements) {
                builder.append(element.text());
            }
            return builder.toString();
        }
    }
}
