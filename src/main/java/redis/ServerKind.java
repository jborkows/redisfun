package redis;

import java.security.SecureRandom;
import java.util.HexFormat;

public sealed interface ServerKind {
    record Master(String masterReplicationId, Long masterReplicationOffset) implements ServerKind {
        public Master() {
            this("master", 0L);
        }

        public Master withNewReplicationId() {
            var random = new SecureRandom();
            var bytes = new byte[20];
            random.nextBytes(bytes);
            var id = HexFormat.of().formatHex(bytes);
            return new Master(id, masterReplicationOffset);
        }
    }

    record Slave(Location replicaOf) implements ServerKind {
    }

}
