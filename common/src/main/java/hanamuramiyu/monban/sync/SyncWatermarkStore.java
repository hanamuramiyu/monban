package hanamuramiyu.monban.sync;

import java.util.Optional;

/**
 * Persistence contract for the accepted snapshot watermark.
 */
public interface SyncWatermarkStore {
    /**
     * Returns the persisted watermark, or {@link Optional#empty()} when no watermark
     * has been persisted yet. A missing file is not an error.
     *
     * @throws SyncStoreException when the watermark exists but cannot be read
     */
    Optional<SyncWatermark> load();

    /**
     * Persists the watermark.
     *
     * @throws SyncStoreException when the watermark cannot be written
     */
    void save(SyncWatermark watermark);
}
