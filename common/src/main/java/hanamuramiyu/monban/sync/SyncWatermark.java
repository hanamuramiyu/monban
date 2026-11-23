package hanamuramiyu.monban.sync;

import java.util.Objects;
import java.util.UUID;

/**
 * Persisted watermark of the last accepted state snapshot: the epoch lineage and
 * the highest revision accepted within it.
 */
public record SyncWatermark(UUID epoch, long revision) {
    public SyncWatermark {
        Objects.requireNonNull(epoch, "epoch");
        if (revision < 1) {
            throw new IllegalArgumentException("Watermark revision must be positive.");
        }
    }
}
