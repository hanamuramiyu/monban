package hanamuramiyu.monban.sync;

/**
 * Reason code for a state snapshot acceptance decision, used for logging and counters.
 */
public enum SyncRejectReason {
    /** The snapshot was accepted. */
    NONE,
    /** Signature, decoding, limits or protocol version failure. */
    INVALID_PAYLOAD,
    /** The snapshot is not newer than the persisted watermark within the same epoch. */
    STALE_REVISION,
    /** The watermark could not be persisted, so the snapshot was not applied. */
    PERSIST_FAILED
}
