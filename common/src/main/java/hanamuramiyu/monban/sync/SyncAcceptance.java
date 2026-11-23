package hanamuramiyu.monban.sync;

import java.util.Objects;

/**
 * Result of a state snapshot acceptance decision. {@code detail} is a log-safe
 * description (revision/epoch of the snapshot and the watermark) and never
 * contains the sync secret.
 *
 * <p>{@code idempotent} marks an equal-revision delivery that was accepted without
 * applying a new state because the already applied state is identical. Such a
 * duplicate is normal traffic and must not trigger rejection logging or a state
 * refresh, while a repaired delivery (equal revision applied because no state was
 * present or the content differed) is a regular acceptance.</p>
 */
public record SyncAcceptance(boolean accepted, SyncRejectReason reason, String detail, boolean idempotent) {
    public SyncAcceptance {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(detail, "detail");
        if (accepted != (reason == SyncRejectReason.NONE)) {
            throw new IllegalArgumentException("Accepted state must match the reject reason.");
        }
        if (idempotent && !accepted) {
            throw new IllegalArgumentException("An idempotent acceptance must be accepted.");
        }
    }

    public static SyncAcceptance accepted(String detail) {
        return new SyncAcceptance(true, SyncRejectReason.NONE, detail, false);
    }

    public static SyncAcceptance duplicate(String detail) {
        return new SyncAcceptance(true, SyncRejectReason.NONE, detail, true);
    }

    public static SyncAcceptance rejected(SyncRejectReason reason, String detail) {
        if (reason == SyncRejectReason.NONE) {
            throw new IllegalArgumentException("A rejection requires a reject reason.");
        }
        return new SyncAcceptance(false, reason, detail, false);
    }
}
