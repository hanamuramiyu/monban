package hanamuramiyu.monban.sync;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Applies state snapshots received from the proxy. Replay protection is based on a
 * persisted watermark: a snapshot is applied only after its watermark has been saved
 * (persist-before-apply). Never throws on input data.
 */
public final class PlayerAccessStateReceiver {
    private final PlayerAccessStateCodec codec;
    private final SyncSecret secret;
    private final SyncWatermarkStore watermarkStore;
    private final AtomicReference<PlayerAccessStateSnapshot> current = new AtomicReference<>();
    private final Map<SyncRejectReason, Long> rejectionCounts = new EnumMap<>(SyncRejectReason.class);
    private SyncWatermark watermark;
    private long idempotentCount;

    public PlayerAccessStateReceiver(PlayerAccessStateCodec codec, SyncSecret secret, SyncWatermarkStore watermarkStore) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.secret = Objects.requireNonNull(secret, "secret");
        this.watermarkStore = Objects.requireNonNull(watermarkStore, "watermarkStore");
        this.watermark = watermarkStore.load().orElse(null);
    }

    /**
     * Decodes and applies a snapshot payload. The order is: decode, acceptance rule,
     * watermark persistence, apply. When the watermark cannot be persisted the snapshot
     * is rejected and the current state is left unchanged.
     *
     * <p>An equal revision within the same epoch is treated as an idempotent repeat
     * delivery (DEC-017). The watermark is already persisted for that revision, so it is
     * not written again. If no state has been applied yet or the applied content differs,
     * the snapshot is applied (backend restart/catch-up); otherwise it is a no-op
     * duplicate reported with {@link SyncAcceptance#idempotent()} set.</p>
     */
    public synchronized SyncAcceptance accept(byte[] payload) {
        PlayerAccessStateSnapshot received;
        try {
            received = codec.decode(payload, secret);
        } catch (RuntimeException exception) {
            return reject(
                    SyncRejectReason.INVALID_PAYLOAD,
                    "invalid payload: " + String.valueOf(exception.getMessage())
            );
        }

        SyncWatermark candidate = new SyncWatermark(received.epoch(), received.revision());
        if (watermark != null && watermark.epoch().equals(candidate.epoch())) {
            if (candidate.revision() < watermark.revision()) {
                return reject(
                        SyncRejectReason.STALE_REVISION,
                        "stale snapshot: revision=" + candidate.revision()
                                + " epoch=" + candidate.epoch()
                                + " watermark revision=" + watermark.revision()
                                + " epoch=" + watermark.epoch()
                );
            }
            if (candidate.revision() == watermark.revision()) {
                return acceptEqualRevision(received, candidate);
            }
        }

        try {
            watermarkStore.save(candidate);
        } catch (RuntimeException exception) {
            return reject(
                    SyncRejectReason.PERSIST_FAILED,
                    "failed to persist watermark for revision=" + candidate.revision()
                            + " epoch=" + candidate.epoch()
                            + ": " + String.valueOf(exception.getMessage())
            );
        }

        SyncWatermark previous = watermark;
        watermark = candidate;
        current.set(received);
        return SyncAcceptance.accepted(
                "accepted revision=" + candidate.revision()
                        + " epoch=" + candidate.epoch()
                        + previousWatermarkDetail(previous)
        );
    }

    public Optional<PlayerAccessStateSnapshot> current() {
        return Optional.ofNullable(current.get());
    }

    public synchronized long rejectionCount(SyncRejectReason reason) {
        Objects.requireNonNull(reason, "reason");
        return rejectionCounts.getOrDefault(reason, 0L);
    }

    /**
     * Number of equal-revision deliveries that were accepted as no-op duplicates
     * (identical content already applied). Repaired deliveries are regular acceptances
     * and are not counted here.
     */
    public synchronized long idempotentCount() {
        return idempotentCount;
    }

    private SyncAcceptance acceptEqualRevision(PlayerAccessStateSnapshot received, SyncWatermark candidate) {
        PlayerAccessStateSnapshot applied = current.get();
        if (applied == null || !sameContent(applied, received)) {
            current.set(received);
            return SyncAcceptance.accepted(
                    "rehydrated revision=" + candidate.revision()
                            + " epoch=" + candidate.epoch()
                            + (applied == null ? " (no applied state)" : " (content differs)")
            );
        }
        idempotentCount++;
        return SyncAcceptance.duplicate(
                "duplicate revision=" + candidate.revision()
                        + " epoch=" + candidate.epoch()
                        + " watermark revision=" + watermark.revision()
                        + " epoch=" + watermark.epoch()
        );
    }

    /**
     * Compares snapshots by their wire content, ignoring epoch/revision. This must not
     * use the domain equality of {@link hanamuramiyu.monban.identity.PlayerIdentity}:
     * for OFFLINE/FLOODGATE that equality ignores {@code technicalUuid}/{@code xuid},
     * so a wire-level change could be treated as "no change".
     */
    private boolean sameContent(PlayerAccessStateSnapshot left, PlayerAccessStateSnapshot right) {
        return Arrays.equals(codec.fingerprint(left), codec.fingerprint(right));
    }

    private SyncAcceptance reject(SyncRejectReason reason, String detail) {
        rejectionCounts.merge(reason, 1L, Long::sum);
        return SyncAcceptance.rejected(reason, detail);
    }

    private String previousWatermarkDetail(SyncWatermark previous) {
        return previous == null
                ? ""
                : " (previous watermark revision=" + previous.revision() + " epoch=" + previous.epoch() + ")";
    }
}
