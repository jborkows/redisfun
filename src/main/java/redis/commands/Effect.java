package redis.commands;

import java.util.List;

public sealed interface Effect {

    String CRLF = "\r\n";

    String text();

    record SimpleString(String value) implements Effect {
        @Override
        public String text() {
            return "+" + value + CRLF;
        }
    }

    record Error(String message) implements Effect {
        @Override
        public String text() {
            return "-ERR " + message + CRLF;
        }
    }

    record WrongType(String message) implements Effect {
        @Override
        public String text() {
            return "-WRONGTYPE " + message + CRLF;
        }
    }

    record Integer(long value) implements Effect {
        @Override
        public String text() {
            return ":" + value + CRLF;
        }
    }

    record BulkString(String value) implements Effect {
        @Override
        public String text() {
            return "$" + value.length() + CRLF + value + CRLF;
        }
    }

    record NullBulk() implements Effect {
        @Override
        public String text() {
            return "$-1" + CRLF;
        }
    }

    record NullArray() implements Effect {
        @Override
        public String text() {
            return "*-1" + CRLF;
        }
    }

    record Array(List<Effect> elements) implements Effect {
        public Array {
            elements = List.copyOf(elements);
        }

        @Override
        public String text() {
            var builder = new StringBuilder();
            builder.append("*").append(elements.size()).append(CRLF);
            for (var element : elements) {
                builder.append(element.text());
            }
            return builder.toString();
        }
    }
}
