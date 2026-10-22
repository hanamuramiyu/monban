package hanamuramiyu.monban.velocity.floodgate;

import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.sync.SyncSecret;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public record ConnectionIdentityEnvelope(String backend, UUID connectionId, PlayerIdentity identity, Instant expiresAt, String signature) {
    public ConnectionIdentityEnvelope {
        Objects.requireNonNull(backend); Objects.requireNonNull(connectionId); Objects.requireNonNull(identity);
        Objects.requireNonNull(expiresAt); Objects.requireNonNull(signature);
        if (identity.type() != hanamuramiyu.monban.identity.IdentityType.FLOODGATE) throw new IllegalArgumentException("Envelope requires FLOODGATE identity");
    }

    public static ConnectionIdentityEnvelope issue(String backend, UUID connectionId, PlayerIdentity identity, Instant expiresAt, SyncSecret secret) {
        String body = body(backend, connectionId, identity, expiresAt);
        return new ConnectionIdentityEnvelope(backend, connectionId, identity, expiresAt, sign(body, secret));
    }

    public boolean verify(SyncSecret secret, Instant now, String expectedBackend) {
        return backend.equals(expectedBackend) && expiresAt.isAfter(now)
                && MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8), sign(body(backend, connectionId, identity, expiresAt), secret).getBytes(StandardCharsets.UTF_8));
    }

    private static String body(String backend, UUID id, PlayerIdentity identity, Instant expiry) {
        return backend + "\n" + id + "\n" + identity.type() + "\n" + identity.xuid().orElseThrow() + "\n" + identity.name() + "\n" + expiry.toEpochMilli();
    }
    private static String sign(String body, SyncSecret secret) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.bytes(), "HmacSHA256")); return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Unable to sign identity envelope", e); }
    }
}
