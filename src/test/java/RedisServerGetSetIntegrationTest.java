import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.Random.class)
class RedisServerGetSetIntegrationTest {

    @Test
    void getForNotExistingKey() throws IOException {
        var response = SharedRedisServer.sendCommand("*2", "$3", "GET", "$1", "A");
        assertEquals(List.of("$-1"), response);
    }

    @Test
    void setKeyAndFetch() throws IOException {
        var response = SharedRedisServer.sendCommand("*3", "$3", "SET", "$1", "B", "$1", "X");
        assertEquals(List.of("+OK"), response);
        response = SharedRedisServer.sendCommand("*2", "$3", "GET", "$1", "B");
        assertEquals(List.of("$1", "X"), response);
    }

}
