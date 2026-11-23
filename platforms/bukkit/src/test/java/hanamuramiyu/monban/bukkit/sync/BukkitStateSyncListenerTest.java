package hanamuramiyu.monban.bukkit.sync;

import hanamuramiyu.monban.sync.PlayerAccessStateCodec;
import hanamuramiyu.monban.sync.PlayerAccessStateReceiver;
import hanamuramiyu.monban.sync.PlayerAccessStateSnapshot;
import hanamuramiyu.monban.sync.RateLimitedLogger;
import hanamuramiyu.monban.sync.SyncChannel;
import hanamuramiyu.monban.sync.SyncSecret;
import hanamuramiyu.monban.sync.SyncWatermark;
import hanamuramiyu.monban.sync.memory.InMemorySyncWatermarkStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitStateSyncListenerTest {
    private static final SyncSecret SECRET = SyncSecret.fromBase64(
            Base64.getEncoder().encodeToString("monban-sync-secret".getBytes(StandardCharsets.UTF_8))
    );
    private static final UUID EPOCH = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    private final PlayerAccessStateCodec codec = new PlayerAccessStateCodec();
    private final InMemorySyncWatermarkStore store = new InMemorySyncWatermarkStore();
    private final AtomicInteger stateChanged = new AtomicInteger();
    private final List<String> debugMessages = new ArrayList<>();
    private final List<String> warnMessages = new ArrayList<>();
    private final AtomicLong nanoClock = new AtomicLong();
    private final BukkitStateSyncListener listener = new BukkitStateSyncListener(
            new PlayerAccessStateReceiver(codec, SECRET, store),
            stateChanged::incrementAndGet,
            debugMessages::add,
            new RateLimitedLogger(warnMessages::add, nanoClock::get, Duration.ofSeconds(60))
    );

    @Test
    void acceptedSnapshotTriggersStateChangeWithoutRejectionLogs() {
        listener.onPluginMessageReceived(SyncChannel.ID, null, codec.encode(snapshot(1), SECRET));
        listener.onPluginMessageReceived(SyncChannel.ID, null, codec.encode(snapshot(2), SECRET));

        assertEquals(2, stateChanged.get());
        assertTrue(debugMessages.isEmpty());
        assertTrue(warnMessages.isEmpty());
    }

    @Test
    void repairedSnapshotAfterRestartTriggersStateChangeWithoutIdempotentDebug() {
        InMemorySyncWatermarkStore restartedStore = new InMemorySyncWatermarkStore();
        restartedStore.save(new SyncWatermark(EPOCH, 1));
        AtomicInteger repairedStateChanged = new AtomicInteger();
        List<String> repairedDebug = new ArrayList<>();
        List<String> repairedWarn = new ArrayList<>();
        BukkitStateSyncListener restartedListener = new BukkitStateSyncListener(
                new PlayerAccessStateReceiver(codec, SECRET, restartedStore),
                repairedStateChanged::incrementAndGet,
                repairedDebug::add,
                new RateLimitedLogger(repairedWarn::add, nanoClock::get, Duration.ofSeconds(60))
        );

        restartedListener.onPluginMessageReceived(SyncChannel.ID, null, codec.encode(snapshot(1), SECRET));

        assertEquals(1, repairedStateChanged.get(), "a repaired snapshot must refresh state");
        assertTrue(repairedWarn.isEmpty());
        assertTrue(repairedDebug.isEmpty(), "repair is a regular acceptance, not an idempotent duplicate");
    }

    @Test
    void duplicateSnapshotLogsDebugOnlyWithoutStateChangeOrWarn() {
        byte[] payload = codec.encode(snapshot(1), SECRET);
        listener.onPluginMessageReceived(SyncChannel.ID, null, payload);
        assertEquals(1, stateChanged.get());

        for (int repeat = 0; repeat < 3; repeat++) {
            listener.onPluginMessageReceived(SyncChannel.ID, null, payload);
        }

        assertEquals(1, stateChanged.get(), "an identical duplicate must not refresh state");
        assertEquals(3, debugMessages.size());
        assertTrue(debugMessages.get(0).contains("idempotent"));
        assertTrue(warnMessages.isEmpty(), "a duplicate is normal traffic and must not warn");
    }

    @Test
    void rejectedSnapshotLogsDebugAlwaysAndWarnRateLimited() {
        listener.onPluginMessageReceived(SyncChannel.ID, null, codec.encode(snapshot(2), SECRET));
        byte[] older = codec.encode(snapshot(1), SECRET);

        for (int repeat = 0; repeat < 3; repeat++) {
            listener.onPluginMessageReceived(SyncChannel.ID, null, older);
        }

        assertEquals(1, stateChanged.get());
        assertEquals(3, debugMessages.size());
        assertTrue(debugMessages.get(0).contains("STALE_REVISION"));
        assertEquals(1, warnMessages.size());
        assertTrue(warnMessages.get(0).contains("STALE_REVISION"));

        nanoClock.addAndGet(Duration.ofSeconds(60).toNanos());
        listener.onPluginMessageReceived(SyncChannel.ID, null, older);

        assertEquals(4, debugMessages.size());
        assertEquals(2, warnMessages.size());
        assertTrue(warnMessages.get(1).contains("+2 similar warnings suppressed"));
    }

    @Test
    void invalidPayloadIsRejectedWithoutThrowing() {
        listener.onPluginMessageReceived(SyncChannel.ID, null, "garbage".getBytes(StandardCharsets.UTF_8));

        assertEquals(0, stateChanged.get());
        assertEquals(1, debugMessages.size());
        assertTrue(debugMessages.get(0).contains("INVALID_PAYLOAD"));
        assertEquals(1, warnMessages.size());
    }

    @Test
    void foreignChannelMessagesAreIgnored() {
        listener.onPluginMessageReceived("other:channel", null, codec.encode(snapshot(1), SECRET));

        assertEquals(0, stateChanged.get());
        assertTrue(debugMessages.isEmpty());
        assertTrue(warnMessages.isEmpty());
    }

    private static PlayerAccessStateSnapshot snapshot(long revision) {
        return new PlayerAccessStateSnapshot(EPOCH, revision, true, List.of(), List.of(), List.of(), List.of());
    }
}
