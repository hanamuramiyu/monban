package hanamuramiyu.monban.sync;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.sync.memory.InMemorySyncWatermarkStore;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAccessStateReceiverTest {
    private static final SyncSecret SECRET = SyncSecret.fromBase64(
            Base64.getEncoder().encodeToString("monban-sync-secret".getBytes(StandardCharsets.UTF_8))
    );
    /** Both textual forms of the secret must stay out of acceptance details (S-13). */
    private static final String SECRET_TEXT_BASE64 = Base64.getEncoder()
            .encodeToString("monban-sync-secret".getBytes(StandardCharsets.UTF_8));
    private static final UUID EPOCH_ONE = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID EPOCH_TWO = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    private static final PlayerIdentity ONLINE = PlayerIdentity.online(
            "Miyu",
            UUID.fromString("00000000-0000-0000-0000-000000000001")
    );

    private final PlayerAccessStateCodec codec = new PlayerAccessStateCodec();

    @Test
    void acceptsFirstSnapshotAndPersistsWatermarkBeforeApplying() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);

        SyncAcceptance acceptance = receiver.accept(codec.encode(snapshot(EPOCH_ONE, 1), SECRET));

        assertTrue(acceptance.accepted());
        assertEquals(SyncRejectReason.NONE, acceptance.reason());
        assertEquals(1, store.saveCount());
        assertEquals(new SyncWatermark(EPOCH_ONE, 1), store.load().orElseThrow());
        assertEquals(1, receiver.current().orElseThrow().revision());
    }

    @Test
    void acceptsEqualRevisionAfterRestartAndRehydrates() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        byte[] sentSnapshot = codec.encode(
                new PlayerAccessStateSnapshot(
                        EPOCH_ONE,
                        3,
                        true,
                        List.of(new AccessGrant(AccessScope.network(), ONLINE)),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                SECRET
        );
        assertTrue(new PlayerAccessStateReceiver(codec, SECRET, store).accept(sentSnapshot).accepted());
        assertEquals(1, store.saveCount());

        PlayerAccessStateReceiver restartedReceiver = new PlayerAccessStateReceiver(codec, SECRET, store);
        SyncAcceptance repair = restartedReceiver.accept(sentSnapshot);

        assertTrue(repair.accepted());
        assertEquals(SyncRejectReason.NONE, repair.reason());
        assertFalse(repair.idempotent());
        assertEquals(3, restartedReceiver.current().orElseThrow().revision());
        assertEquals(1, restartedReceiver.current().orElseThrow().accessGrants().size());
        assertEquals(1, store.saveCount(), "repair must not write the watermark again");
        assertEquals(0, restartedReceiver.idempotentCount());
    }

    @Test
    void acceptsEqualAndNewerRevisionsWithinEpoch() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);
        byte[] first = codec.encode(snapshot(EPOCH_ONE, 1), SECRET);
        byte[] second = codec.encode(snapshot(EPOCH_ONE, 2), SECRET);

        assertTrue(receiver.accept(first).accepted());
        SyncAcceptance duplicateFirst = receiver.accept(first);
        assertTrue(duplicateFirst.accepted());
        assertTrue(duplicateFirst.idempotent());
        assertEquals(SyncRejectReason.NONE, duplicateFirst.reason());

        assertTrue(receiver.accept(second).accepted());
        assertEquals(SyncRejectReason.STALE_REVISION, receiver.accept(first).reason());

        SyncAcceptance duplicateSecond = receiver.accept(second);
        assertTrue(duplicateSecond.accepted());
        assertTrue(duplicateSecond.idempotent());

        assertEquals(2, receiver.current().orElseThrow().revision());
        assertEquals(1, receiver.rejectionCount(SyncRejectReason.STALE_REVISION));
        assertEquals(2, receiver.idempotentCount());
    }

    @Test
    void acceptsEqualRevisionAsIdempotentDuplicateWithoutWatermarkWrite() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);
        byte[] payload = codec.encode(snapshot(EPOCH_ONE, 1), SECRET);
        assertTrue(receiver.accept(payload).accepted());
        PlayerAccessStateSnapshot applied = receiver.current().orElseThrow();

        SyncAcceptance duplicate = receiver.accept(payload);

        assertTrue(duplicate.accepted());
        assertTrue(duplicate.idempotent());
        assertEquals(SyncRejectReason.NONE, duplicate.reason());
        assertEquals(1, store.saveCount(), "duplicate must not write the watermark");
        assertEquals(applied, receiver.current().orElseThrow());
        assertEquals(1, receiver.idempotentCount());
        assertEquals(0, receiver.rejectionCount(SyncRejectReason.NONE));
    }

    @Test
    void acceptsEqualRevisionWithDifferentContentAsRepairWithoutWatermarkWrite() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);
        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 1), SECRET)).accepted());

        byte[] changed = codec.encode(
                new PlayerAccessStateSnapshot(
                        EPOCH_ONE,
                        1,
                        true,
                        List.of(new AccessGrant(AccessScope.network(), ONLINE)),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                SECRET
        );
        SyncAcceptance repair = receiver.accept(changed);

        assertTrue(repair.accepted());
        assertFalse(repair.idempotent());
        assertEquals(1, store.saveCount(), "repair must not write the watermark");
        assertEquals(1, receiver.current().orElseThrow().accessGrants().size());
        assertEquals(0, receiver.idempotentCount());
    }

    @Test
    void acceptsNewerRevisionAfterDuplicate() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);

        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 1), SECRET)).accepted());
        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 1), SECRET)).idempotent());
        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 2), SECRET)).accepted());

        assertEquals(new SyncWatermark(EPOCH_ONE, 2), store.load().orElseThrow());
        assertEquals(2, receiver.current().orElseThrow().revision());
        assertEquals(2, store.saveCount(), "watermark saved for revision 1 and 2 only");
    }

    @Test
    void acceptsNewEpochAndReplacesWatermark() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);
        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 5), SECRET)).accepted());

        SyncAcceptance acceptance = receiver.accept(codec.encode(snapshot(EPOCH_TWO, 1), SECRET));

        assertTrue(acceptance.accepted());
        assertEquals(new SyncWatermark(EPOCH_TWO, 1), store.load().orElseThrow());
        assertEquals(EPOCH_TWO, receiver.current().orElseThrow().epoch());
    }

    @Test
    void acceptsEqualRevisionInNewEpochAfterWatermark() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);
        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 3), SECRET)).accepted());

        SyncAcceptance acceptance = receiver.accept(codec.encode(snapshot(EPOCH_TWO, 3), SECRET));

        assertTrue(acceptance.accepted(), "an equal revision in a new epoch must not be stale");
        assertEquals(SyncRejectReason.NONE, acceptance.reason());
        assertFalse(acceptance.idempotent());
        assertEquals(EPOCH_TWO, receiver.current().orElseThrow().epoch());
        assertEquals(3, receiver.current().orElseThrow().revision());
        assertEquals(new SyncWatermark(EPOCH_TWO, 3), store.load().orElseThrow());
        assertEquals(0, receiver.rejectionCount(SyncRejectReason.STALE_REVISION));
    }

    @Test
    void rejectsSnapshotWhenWatermarkCannotBePersisted() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);
        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 1), SECRET)).accepted());
        store.failNextSave();

        SyncAcceptance acceptance = assertDoesNotThrow(
                () -> receiver.accept(codec.encode(snapshot(EPOCH_ONE, 2), SECRET))
        );

        assertFalse(acceptance.accepted());
        assertEquals(SyncRejectReason.PERSIST_FAILED, acceptance.reason());
        assertEquals(1, receiver.current().orElseThrow().revision());
        assertEquals(new SyncWatermark(EPOCH_ONE, 1), store.load().orElseThrow());
    }

    @Test
    void rejectsInvalidPayloadsWithoutThrowing() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);
        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 1), SECRET)).accepted());

        byte[] garbage = "not a state snapshot".getBytes(StandardCharsets.UTF_8);
        byte[] foreignSignature = codec.encode(snapshot(EPOCH_TWO, 9), SECRET);
        foreignSignature[0] ^= 1;
        byte[] versionThree = signedVersionThreePayload(7);

        for (byte[] payload : List.of(garbage, foreignSignature, versionThree)) {
            SyncAcceptance acceptance = assertDoesNotThrow(() -> receiver.accept(payload));
            assertFalse(acceptance.accepted());
            assertEquals(SyncRejectReason.INVALID_PAYLOAD, acceptance.reason());
        }

        assertEquals(1, receiver.current().orElseThrow().revision());
        assertEquals(new SyncWatermark(EPOCH_ONE, 1), store.load().orElseThrow());
    }

    @Test
    void constructorFailsClosedWhenWatermarkCannotBeLoaded() {
        SyncWatermarkStore brokenStore = new SyncWatermarkStore() {
            @Override
            public Optional<SyncWatermark> load() {
                throw new SyncStoreException("corrupted sync-watermark.yml");
            }

            @Override
            public void save(SyncWatermark watermark) {
                throw new AssertionError("save must not be called when loading failed");
            }
        };

        assertThrows(
                SyncStoreException.class,
                () -> new PlayerAccessStateReceiver(codec, SECRET, brokenStore)
        );
    }

    @Test
    void acceptanceDetailsNeverContainTheSyncSecret() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);
        List<SyncAcceptance> decisions = new ArrayList<>();
        decisions.add(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 1), SECRET)));
        decisions.add(receiver.accept(new byte[0]));
        store.failNextSave();
        decisions.add(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 2), SECRET)));
        decisions.add(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 1), SECRET)));

        assertEquals(
                List.of(
                        SyncRejectReason.NONE,
                        SyncRejectReason.INVALID_PAYLOAD,
                        SyncRejectReason.PERSIST_FAILED,
                        SyncRejectReason.NONE
                ),
                decisions.stream().map(SyncAcceptance::reason).toList()
        );
        assertTrue(decisions.get(3).idempotent(), "equal revision must be an idempotent duplicate");
        for (SyncAcceptance decision : decisions) {
            assertFalse(decision.detail().contains("monban-sync-secret"), decision.detail());
            assertFalse(decision.detail().contains(SECRET_TEXT_BASE64), decision.detail());
        }
    }

    @Test
    void countsRejectionsPerReason() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);

        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 1), SECRET)).accepted());
        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 1), SECRET)).idempotent());
        assertDoesNotThrow(() -> receiver.accept(new byte[0]));
        store.failNextSave();
        assertEquals(
                SyncRejectReason.PERSIST_FAILED,
                receiver.accept(codec.encode(snapshot(EPOCH_ONE, 2), SECRET)).reason()
        );
        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 2), SECRET)).accepted());
        assertEquals(
                SyncRejectReason.STALE_REVISION,
                receiver.accept(codec.encode(snapshot(EPOCH_ONE, 1), SECRET)).reason()
        );

        assertEquals(0, receiver.rejectionCount(SyncRejectReason.NONE));
        assertEquals(1, receiver.rejectionCount(SyncRejectReason.STALE_REVISION));
        assertEquals(1, receiver.rejectionCount(SyncRejectReason.PERSIST_FAILED));
        assertEquals(1, receiver.rejectionCount(SyncRejectReason.INVALID_PAYLOAD));
        assertEquals(1, receiver.idempotentCount());
    }

    @Test
    void persistFailureDoesNotAdvanceTheInternalWatermark() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);
        byte[] first = codec.encode(snapshot(EPOCH_ONE, 1), SECRET);
        byte[] second = codec.encode(snapshot(EPOCH_ONE, 2), SECRET);
        assertTrue(receiver.accept(first).accepted());
        store.failNextSave();

        assertEquals(SyncRejectReason.PERSIST_FAILED, receiver.accept(second).reason());

        assertTrue(receiver.accept(second).accepted());
        assertEquals(2, receiver.current().orElseThrow().revision());
        assertEquals(new SyncWatermark(EPOCH_ONE, 2), store.load().orElseThrow());
        assertEquals(2, store.saveCount());
        assertEquals(1, receiver.rejectionCount(SyncRejectReason.PERSIST_FAILED));
    }

    @Test
    void invalidPayloadOnFreshReceiverDoesNotApplyOrPersist() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);

        assertInvalidPayload(receiver, null);
        assertInvalidPayload(receiver, new byte[0]);
        assertInvalidPayload(receiver, new byte[31]);

        assertTrue(receiver.current().isEmpty());
        assertTrue(store.load().isEmpty());
        assertEquals(0, store.saveCount());
        assertEquals(3, receiver.rejectionCount(SyncRejectReason.INVALID_PAYLOAD));
    }

    @Test
    void restartRejectsOlderRevisionAndAcceptsNewerRevision() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        assertTrue(new PlayerAccessStateReceiver(codec, SECRET, store)
                .accept(codec.encode(snapshot(EPOCH_ONE, 3), SECRET)).accepted());

        PlayerAccessStateReceiver restarted = new PlayerAccessStateReceiver(codec, SECRET, store);

        assertEquals(
                SyncRejectReason.STALE_REVISION,
                restarted.accept(codec.encode(snapshot(EPOCH_ONE, 2), SECRET)).reason()
        );
        assertTrue(restarted.current().isEmpty());
        assertTrue(restarted.accept(codec.encode(snapshot(EPOCH_ONE, 4), SECRET)).accepted());
        assertEquals(4, restarted.current().orElseThrow().revision());
    }

    @Test
    void persistFailureForNewEpochKeepsThePreviousWatermark() {
        InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);
        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 5), SECRET)).accepted());
        store.failNextSave();

        SyncAcceptance failed = receiver.accept(codec.encode(snapshot(EPOCH_TWO, 1), SECRET));

        assertEquals(SyncRejectReason.PERSIST_FAILED, failed.reason());
        assertEquals(EPOCH_ONE, receiver.current().orElseThrow().epoch());
        assertEquals(new SyncWatermark(EPOCH_ONE, 5), store.load().orElseThrow());

        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_TWO, 1), SECRET)).accepted());
        assertEquals(new SyncWatermark(EPOCH_TWO, 1), store.load().orElseThrow());
        assertEquals(EPOCH_TWO, receiver.current().orElseThrow().epoch());
    }

    @Test
    void persistsWatermarkBeforeApplyingSnapshot() {
        AtomicReference<PlayerAccessStateReceiver> receiverReference = new AtomicReference<>();
        boolean[] stateVisibleDuringSave = new boolean[1];
        SyncWatermarkStore store = new SyncWatermarkStore() {
            private SyncWatermark stored;

            @Override
            public Optional<SyncWatermark> load() {
                return Optional.ofNullable(stored);
            }

            @Override
            public void save(SyncWatermark watermark) {
                PlayerAccessStateReceiver receiver = receiverReference.get();
                stateVisibleDuringSave[0] = receiver != null && receiver.current().isPresent();
                stored = watermark;
            }
        };

        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET, store);
        receiverReference.set(receiver);

        assertTrue(receiver.accept(codec.encode(snapshot(EPOCH_ONE, 1), SECRET)).accepted());

        assertFalse(stateVisibleDuringSave[0]);
        assertEquals(1, receiver.current().orElseThrow().revision());
    }

    private static void assertInvalidPayload(PlayerAccessStateReceiver receiver, byte[] payload) {
        SyncAcceptance acceptance = assertDoesNotThrow(() -> receiver.accept(payload));
        assertFalse(acceptance.accepted());
        assertEquals(SyncRejectReason.INVALID_PAYLOAD, acceptance.reason());
    }

    private static PlayerAccessStateSnapshot snapshot(UUID epoch, long revision) {
        return new PlayerAccessStateSnapshot(epoch, revision, true, List.of(), List.of(), List.of(), List.of());
    }

    private static byte[] signedVersionThreePayload(long revision) {
        try {
            java.io.ByteArrayOutputStream bodyBytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream body = new java.io.DataOutputStream(bodyBytes);
            body.writeInt(0x4D425354);
            body.writeInt(3);
            body.writeLong(revision);
            body.writeBoolean(true);
            body.writeInt(0);
            body.writeInt(0);
            body.writeInt(0);
            body.writeInt(0);
            body.flush();

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.bytes(), "HmacSHA256"));
            byte[] content = bodyBytes.toByteArray();
            java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream();
            result.write(content);
            result.write(mac.doFinal(content));
            return result.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
