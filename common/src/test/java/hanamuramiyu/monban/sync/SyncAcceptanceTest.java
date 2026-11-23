package hanamuramiyu.monban.sync;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncAcceptanceTest {
    private static final UUID EPOCH = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void acceptedMatchesNoneReason() {
        SyncAcceptance acceptance = SyncAcceptance.accepted("detail");

        assertTrue(acceptance.accepted());
        assertEquals(SyncRejectReason.NONE, acceptance.reason());
        assertFalse(acceptance.idempotent());
    }

    @Test
    void rejectedCarriesReason() {
        SyncAcceptance acceptance = SyncAcceptance.rejected(SyncRejectReason.STALE_REVISION, "detail");

        assertFalse(acceptance.accepted());
        assertEquals(SyncRejectReason.STALE_REVISION, acceptance.reason());
        assertFalse(acceptance.idempotent());
    }

    @Test
    void duplicateIsAcceptedNoneAndIdempotent() {
        SyncAcceptance acceptance = SyncAcceptance.duplicate("detail");

        assertTrue(acceptance.accepted());
        assertEquals(SyncRejectReason.NONE, acceptance.reason());
        assertTrue(acceptance.idempotent());
    }

    @Test
    void rejectsMismatchedAcceptedState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncAcceptance(true, SyncRejectReason.STALE_REVISION, "detail", false)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncAcceptance(false, SyncRejectReason.NONE, "detail", false)
        );
    }

    @Test
    void idempotentRequiresAcceptedState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncAcceptance(false, SyncRejectReason.STALE_REVISION, "detail", true)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncAcceptance(false, SyncRejectReason.INVALID_PAYLOAD, "detail", true)
        );
    }

    @Test
    void rejectsNoneReasonForRejection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SyncAcceptance.rejected(SyncRejectReason.NONE, "detail")
        );
    }

    @Test
    void watermarkValidatesEpochAndRevision() {
        assertEquals(new SyncWatermark(EPOCH, 1), new SyncWatermark(EPOCH, 1));
        assertThrows(NullPointerException.class, () -> new SyncWatermark(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new SyncWatermark(EPOCH, 0));
    }

    @Test
    void rejectReasonCoversAllFailureModes() {
        assertEquals(List.of(
                SyncRejectReason.NONE,
                SyncRejectReason.INVALID_PAYLOAD,
                SyncRejectReason.STALE_REVISION,
                SyncRejectReason.PERSIST_FAILED
        ), List.of(SyncRejectReason.values()));
    }
}
