import java.io.IOException;

@FunctionalInterface
interface NetworkCall<V> {
    V get() throws IOException;
}
