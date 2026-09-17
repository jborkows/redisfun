import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

class Dispatcher{
        private static final Logger logger = Logger.getLogger(Dispatcher.class.getName());

        private final InputStream inputStream;
        private final OutputStream outputStream;

    Dispatcher(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    void process() throws IOException {
        var buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            final int received = bytesRead; // final copy: lambdas can't capture a reassigned variable
            logger.fine(() -> "Received: " + new String(buffer, 0, received));
            writeOutput("+PONG");
        }
    }

    private void writeOutput(String text) throws IOException {
        outputStream.write((text+"\r\n").getBytes());
    }

}
