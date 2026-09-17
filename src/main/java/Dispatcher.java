import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class Dispatcher{
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
            Logger.info("Received: " + new String(buffer, 0, bytesRead));
            writeOutput("+PONG");
        }
    }

    private void writeOutput(String text) throws IOException {
        outputStream.write((text+"\r\n").getBytes());
    }

}
