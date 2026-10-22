package hanamuramiyu.monban.velocity.floodgate;

import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.sync.SyncSecret;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionIdentityEnvelopeTest {
    private final SyncSecret secret = SyncSecret.of("0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test void verifiesTargetAndExpiry() {
        var identity = PlayerIdentity.floodgate("Bedrock", "1234567890");
        var envelope = ConnectionIdentityEnvelope.issue("lobby", UUID.randomUUID(), identity, Instant.now().plusSeconds(30), secret);
        assertTrue(envelope.verify(secret, Instant.now(), "lobby"));
        assertFalse(envelope.verify(secret, Instant.now(), "survival"));
        assertFalse(envelope.verify(secret, Instant.now().plusSeconds(31), "lobby"));
    }
}
