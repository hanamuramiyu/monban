package hanamuramiyu.monban.velocity.sync;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupRepository;
import hanamuramiyu.monban.access.permission.memory.InMemoryPlayerPermissionGrantRepository;
import hanamuramiyu.monban.storage.file.sync.FileSyncEpochStore;
import hanamuramiyu.monban.sync.PlayerAccessStateCodec;
import hanamuramiyu.monban.sync.PlayerAccessStateSnapshot;
import hanamuramiyu.monban.sync.RateLimitedLogger;
import hanamuramiyu.monban.sync.SyncSecret;
import hanamuramiyu.monban.sync.SyncStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityStateSynchronizerTest {
    private static final SyncSecret SECRET = SyncSecret.fromBase64(
            Base64.getEncoder().encodeToString("monban-sync-secret".getBytes(StandardCharsets.UTF_8))
    );
    private static final PlayerIdentity ONLINE = PlayerIdentity.online(
            "Miyu",
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")
    );

    @Test
    void registersChannelAndSendsSignedSnapshot() {
        AtomicReference<ChannelIdentifier> registered = new AtomicReference<>();
        AtomicReference<byte[]> payload = new AtomicReference<>();
        RegisteredServer backend = registeredServer("survival", payload);
        ProxyServer proxy = proxyServer(List.of(backend), registered);
        VelocityStateSynchronizer synchronizer = synchronizer(proxy);

        synchronizer.registerChannel();
        synchronizer.broadcast();

        assertEquals(VelocityStateSynchronizer.CHANNEL, registered.get());
        assertTrue(payload.get() != null && payload.get().length > 32);
        assertEquals(1, new PlayerAccessStateCodec().decode(payload.get(), SECRET).revision());

        synchronizer.unregisterChannel();
    }

    @Test
    void blocksClientMessagesOnStateChannel() {
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
        PluginMessageEvent event = new PluginMessageEvent(
                player,
                player,
                VelocityStateSynchronizer.CHANNEL,
                new byte[0]
        );

        synchronizer(proxyServer(List.of(), new AtomicReference<>())).onPluginMessage(event);

        assertFalse(event.getResult().isAllowed());
    }

    @Test
    void preservesRevisionAcrossSynchronizerRestart() throws Exception {
        Path revisionFile = Files.createTempFile("monban-state-revision", ".txt");
        Files.delete(revisionFile);
        AtomicReference<byte[]> firstPayload = new AtomicReference<>();
        AtomicReference<byte[]> secondPayload = new AtomicReference<>();
        RegisteredServer firstBackend = registeredServer("survival", firstPayload);
        RegisteredServer secondBackend = registeredServer("survival", secondPayload);

        synchronizer(proxyServer(List.of(firstBackend), new AtomicReference()), revisionFile).broadcast();
        synchronizer(proxyServer(List.of(secondBackend), new AtomicReference()), revisionFile).broadcast();

        assertEquals(1, new PlayerAccessStateCodec().decode(firstPayload.get(), SECRET).revision());
        assertEquals(2, new PlayerAccessStateCodec().decode(secondPayload.get(), SECRET).revision());
        Files.deleteIfExists(revisionFile);
    }

    @Test
    void generatesEpochWhenAbsentAndPreservesItAcrossRestart(@TempDir Path dataDirectory) {
        Path revisionFile = dataDirectory.resolve("state-revision");
        Path epochFile = dataDirectory.resolve("state-epoch");
        AtomicReference<byte[]> firstPayload = new AtomicReference<>();
        AtomicReference<byte[]> secondPayload = new AtomicReference<>();

        persistingSynchronizer(revisionFile, epochFile, firstPayload).broadcast();
        persistingSynchronizer(revisionFile, epochFile, secondPayload).broadcast();

        PlayerAccessStateSnapshot first = new PlayerAccessStateCodec().decode(firstPayload.get(), SECRET);
        PlayerAccessStateSnapshot second = new PlayerAccessStateCodec().decode(secondPayload.get(), SECRET);
        assertEquals(1, first.revision());
        assertEquals(2, second.revision());
        assertEquals(first.epoch(), second.epoch());
        assertTrue(Files.exists(epochFile));
    }

    @Test
    void lostRevisionFileStartsNewEpochWithRevisionOne(@TempDir Path dataDirectory) throws IOException {
        Path revisionFile = dataDirectory.resolve("state-revision");
        Path epochFile = dataDirectory.resolve("state-epoch");
        AtomicReference<byte[]> firstPayload = new AtomicReference<>();
        AtomicReference<byte[]> secondPayload = new AtomicReference<>();

        persistingSynchronizer(revisionFile, epochFile, firstPayload).broadcast();
        Files.delete(revisionFile);
        persistingSynchronizer(revisionFile, epochFile, secondPayload).broadcast();

        PlayerAccessStateSnapshot first = new PlayerAccessStateCodec().decode(firstPayload.get(), SECRET);
        PlayerAccessStateSnapshot second = new PlayerAccessStateCodec().decode(secondPayload.get(), SECRET);
        assertEquals(1, first.revision());
        assertEquals(1, second.revision());
        assertNotEquals(first.epoch(), second.epoch());
    }

    @Test
    void lostEpochFileStartsNewEpochWithRevisionOne(@TempDir Path dataDirectory) throws IOException {
        Path revisionFile = dataDirectory.resolve("state-revision");
        Path epochFile = dataDirectory.resolve("state-epoch");
        AtomicReference<byte[]> firstPayload = new AtomicReference<>();
        AtomicReference<byte[]> secondPayload = new AtomicReference<>();

        persistingSynchronizer(revisionFile, epochFile, firstPayload).broadcast();
        Files.delete(epochFile);
        persistingSynchronizer(revisionFile, epochFile, secondPayload).broadcast();

        PlayerAccessStateSnapshot first = new PlayerAccessStateCodec().decode(firstPayload.get(), SECRET);
        PlayerAccessStateSnapshot second = new PlayerAccessStateCodec().decode(secondPayload.get(), SECRET);
        assertEquals(1, first.revision());
        assertEquals(1, second.revision());
        assertNotEquals(first.epoch(), second.epoch());
    }

    @Test
    void zeroRevisionFileStartsNewEpoch(@TempDir Path dataDirectory) throws IOException {
        Path revisionFile = dataDirectory.resolve("state-revision");
        Path epochFile = dataDirectory.resolve("state-epoch");
        AtomicReference<byte[]> firstPayload = new AtomicReference<>();
        AtomicReference<byte[]> secondPayload = new AtomicReference<>();

        persistingSynchronizer(revisionFile, epochFile, firstPayload).broadcast();
        Files.writeString(revisionFile, "0", StandardCharsets.UTF_8);
        persistingSynchronizer(revisionFile, epochFile, secondPayload).broadcast();

        PlayerAccessStateSnapshot first = new PlayerAccessStateCodec().decode(firstPayload.get(), SECRET);
        PlayerAccessStateSnapshot second = new PlayerAccessStateCodec().decode(secondPayload.get(), SECRET);
        assertEquals(1, first.revision());
        assertEquals(1, second.revision());
        assertNotEquals(first.epoch(), second.epoch());
    }

    @Test
    void failsStartupWhenEpochCannotBePersisted(@TempDir Path dataDirectory) throws IOException {
        Path blocker = dataDirectory.resolve("blocked");
        Files.createFile(blocker);
        FileSyncEpochStore epochStore = new FileSyncEpochStore(blocker.resolve("state-epoch"));

        assertThrows(
                SyncStoreException.class,
                () -> new VelocityStateSynchronizer(
                        proxyServer(List.of(), new AtomicReference<>()),
                        new InMemoryAccessGrantRepository(),
                        new InMemoryPlayerGroupRepository(),
                        new InMemoryPlayerGroupAssignmentRepository(),
                        new InMemoryPlayerPermissionGrantRepository(),
                        SECRET,
                        logger(),
                        dataDirectory.resolve("state-revision"),
                        () -> true,
                        epochStore
                )
        );
    }

    @Test
    void keepsBroadcastingWhenRevisionSaveFails() {
        List<String> warnings = new ArrayList<>();
        AtomicLong nanoClock = new AtomicLong();
        AtomicReference<byte[]> payload = new AtomicReference<>();

        VelocityStateSynchronizer synchronizer = new VelocityStateSynchronizer(
                proxyServer(List.of(registeredServer("survival", payload)), new AtomicReference<>()),
                new InMemoryAccessGrantRepository(),
                new InMemoryPlayerGroupRepository(),
                new InMemoryPlayerGroupAssignmentRepository(),
                new InMemoryPlayerPermissionGrantRepository(),
                SECRET,
                logger(),
                failingRevisionStore(),
                null,
                () -> true,
                new RateLimitedLogger(warnings::add, nanoClock::get, Duration.ofSeconds(60))
        );

        assertDoesNotThrow(synchronizer::broadcast);
        assertDoesNotThrow(synchronizer::broadcast);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("Failed to persist state revision"));

        nanoClock.addAndGet(Duration.ofSeconds(60).toNanos());
        assertDoesNotThrow(synchronizer::broadcast);
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(1).contains("+1 similar warnings suppressed"));

        assertEquals(3, new PlayerAccessStateCodec().decode(payload.get(), SECRET).revision());
    }

    @Test
    void firstSnapshotIsAlwaysPublished() {
        Fixture fixture = new Fixture();

        fixture.synchronizer.broadcast();

        assertEquals(1, fixture.lastRevision());
        assertEquals(1, fixture.store.successfulSaves());
        assertEquals(1, fixture.store.attempts());
    }

    @Test
    void repeatedConnectDoesNotIncreaseRevision() {
        Fixture fixture = new Fixture();
        Player player = playerOn(fixture.backend);

        fixture.synchronizer.onServerPostConnect(new ServerPostConnectEvent(player, fixture.backend));
        fixture.synchronizer.onServerPostConnect(new ServerPostConnectEvent(player, fixture.backend));

        assertEquals(1, fixture.lastRevision());
        assertEquals(1, fixture.store.successfulSaves());
        assertEquals(2, fixture.sentPayloads.size());
        assertEquals(1, new PlayerAccessStateCodec().decode(fixture.sentPayloads.get(0), SECRET).revision());
        assertEquals(1, new PlayerAccessStateCodec().decode(fixture.sentPayloads.get(1), SECRET).revision());
    }

    @Test
    void connectEventsShareOneSnapshotWithoutDoubleIncrement() {
        Fixture fixture = new Fixture();
        Player player = playerOn(fixture.backend);

        fixture.synchronizer.onPlayerChooseInitialServer(
                new PlayerChooseInitialServerEvent(player, fixture.backend));
        fixture.synchronizer.onServerPostConnect(new ServerPostConnectEvent(player, fixture.backend));

        assertEquals(1, fixture.lastRevision());
        assertEquals(1, fixture.store.successfulSaves());
        assertEquals(2, fixture.sentPayloads.size());
    }

    @Test
    void stateChangeIncreasesRevision() {
        Fixture fixture = new Fixture();
        fixture.synchronizer.broadcast();

        fixture.grants.add(new AccessGrant(AccessScope.network(), ONLINE));
        fixture.synchronizer.broadcast();

        assertEquals(2, fixture.lastRevision());
        assertEquals(2, fixture.store.successfulSaves());
    }

    @Test
    void whitelistToggleIncreasesRevision() {
        Fixture fixture = new Fixture();
        fixture.synchronizer.broadcast();
        assertEquals(1, fixture.lastRevision());

        fixture.whitelist.set(false);
        fixture.synchronizer.broadcast();

        assertEquals(2, fixture.lastRevision());
        assertEquals(2, fixture.store.successfulSaves());
        assertFalse(fixture.lastSnapshot().networkWhitelistEnabled());
    }

    @Test
    void duplicateSnapshotIsStillSentToBackends() {
        Fixture fixture = new Fixture();

        fixture.synchronizer.broadcast();
        fixture.synchronizer.broadcast();

        assertEquals(1, fixture.lastRevision());
        assertEquals(1, fixture.store.successfulSaves());
        assertEquals(2, fixture.sentPayloads.size(), "an unchanged snapshot must still be delivered");
        assertEquals(
                1,
                new PlayerAccessStateCodec().decode(fixture.sentPayloads.get(1), SECRET).revision()
        );
    }

    @Test
    void failedRevisionSaveForcesNextSnapshotToBump() {
        Fixture fixture = new Fixture();
        fixture.store.failNextSaves(1);

        fixture.synchronizer.broadcast();
        assertEquals(1, fixture.lastRevision());

        fixture.synchronizer.broadcast();
        assertEquals(2, fixture.lastRevision());
        assertEquals(1, fixture.store.successfulSaves());
        assertEquals(2, fixture.store.attempts());

        fixture.synchronizer.broadcast();
        assertEquals(2, fixture.lastRevision(), "after a successful retry the revision is reused");
        assertEquals(2, fixture.store.attempts());
    }

    @Test
    void stateChangeAndRevertOnlyBumpsWhileDifferent() {
        Fixture fixture = new Fixture();
        AccessGrant grant = new AccessGrant(AccessScope.network(), ONLINE);

        fixture.synchronizer.broadcast();
        fixture.grants.add(grant);
        fixture.synchronizer.broadcast();
        fixture.grants.remove(grant.scope(), grant.identity());
        fixture.synchronizer.broadcast();
        fixture.synchronizer.broadcast();

        assertEquals(List.of(1L, 2L, 3L, 3L), fixture.sentRevisions());
        assertEquals(3, fixture.store.successfulSaves());
    }

    private static StateRevisionStore failingRevisionStore() {
        return new StateRevisionStore() {
            @Override
            public long load() {
                return 0;
            }

            @Override
            public void save(long revision) {
                throw new IllegalStateException("Injected state revision failure.");
            }
        };
    }

    private static VelocityStateSynchronizer persistingSynchronizer(
            Path revisionFile,
            Path epochFile,
            AtomicReference<byte[]> payload
    ) {
        return new VelocityStateSynchronizer(
                proxyServer(List.of(registeredServer("survival", payload)), new AtomicReference<>()),
                new InMemoryAccessGrantRepository(),
                new InMemoryPlayerGroupRepository(),
                new InMemoryPlayerGroupAssignmentRepository(),
                new InMemoryPlayerPermissionGrantRepository(),
                SECRET,
                logger(),
                revisionFile,
                () -> true,
                new FileSyncEpochStore(epochFile)
        );
    }

    private static VelocityStateSynchronizer synchronizer(ProxyServer server) {
        return synchronizer(server, null);
    }

    private static VelocityStateSynchronizer synchronizer(ProxyServer server, Path revisionFile) {
        return new VelocityStateSynchronizer(
                server,
                new InMemoryAccessGrantRepository(),
                new InMemoryPlayerGroupRepository(),
                new InMemoryPlayerGroupAssignmentRepository(),
                new InMemoryPlayerPermissionGrantRepository(),
                SECRET,
                logger(),
                revisionFile
        );
    }

    private static RegisteredServer registeredServer(String name, AtomicReference<byte[]> payload) {
        ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565));
        return (RegisteredServer) Proxy.newProxyInstance(
                RegisteredServer.class.getClassLoader(),
                new Class<?>[]{RegisteredServer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getServerInfo" -> info;
                    case "sendPluginMessage" -> {
                        payload.set((byte[]) args[1]);
                        yield true;
                    }
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static RegisteredServer registeredServer(String name, List<byte[]> sentPayloads) {
        ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565));
        return (RegisteredServer) Proxy.newProxyInstance(
                RegisteredServer.class.getClassLoader(),
                new Class<?>[]{RegisteredServer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getServerInfo" -> info;
                    case "sendPluginMessage" -> {
                        sentPayloads.add((byte[]) args[1]);
                        yield true;
                    }
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Player playerOn(RegisteredServer backend) {
        ServerConnection connection = (ServerConnection) Proxy.newProxyInstance(
                ServerConnection.class.getClassLoader(),
                new Class<?>[]{ServerConnection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getServer" -> backend;
                    default -> defaultValue(method.getReturnType());
                }
        );
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCurrentServer" -> Optional.of(connection);
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static final class Fixture {
        private final InMemoryAccessGrantRepository grants = new InMemoryAccessGrantRepository();
        private final InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        private final InMemoryPlayerGroupAssignmentRepository assignments =
                new InMemoryPlayerGroupAssignmentRepository();
        private final InMemoryPlayerPermissionGrantRepository permissions =
                new InMemoryPlayerPermissionGrantRepository();
        private final RecordingRevisionStore store = new RecordingRevisionStore();
        private final AtomicBoolean whitelist = new AtomicBoolean(true);
        private final List<byte[]> sentPayloads = new ArrayList<>();
        private final RegisteredServer backend = registeredServer("survival", sentPayloads);
        private final VelocityStateSynchronizer synchronizer = new VelocityStateSynchronizer(
                proxyServer(List.of(backend), new AtomicReference<>()),
                grants,
                groups,
                assignments,
                permissions,
                SECRET,
                logger(),
                store,
                null,
                whitelist::get,
                new RateLimitedLogger(message -> { }, System::nanoTime)
        );

        private PlayerAccessStateSnapshot lastSnapshot() {
            return new PlayerAccessStateCodec().decode(sentPayloads.get(sentPayloads.size() - 1), SECRET);
        }

        private long lastRevision() {
            return lastSnapshot().revision();
        }

        private List<Long> sentRevisions() {
            PlayerAccessStateCodec codec = new PlayerAccessStateCodec();
            return sentPayloads.stream().map(payload -> codec.decode(payload, SECRET).revision()).toList();
        }
    }

    private static final class RecordingRevisionStore implements StateRevisionStore {
        private long revision;
        private int attempts;
        private int successfulSaves;
        private int failuresRemaining;

        @Override
        public long load() {
            return revision;
        }

        @Override
        public void save(long value) {
            attempts++;
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new IllegalStateException("Injected state revision failure.");
            }
            revision = value;
            successfulSaves++;
        }

        private void failNextSaves(int count) {
            failuresRemaining = count;
        }

        private int attempts() {
            return attempts;
        }

        private int successfulSaves() {
            return successfulSaves;
        }
    }

    private static ProxyServer proxyServer(
            Collection<RegisteredServer> servers,
            AtomicReference<ChannelIdentifier> registered
    ) {
        ChannelRegistrar registrar = (ChannelRegistrar) Proxy.newProxyInstance(
                ChannelRegistrar.class.getClassLoader(),
                new Class<?>[]{ChannelRegistrar.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("register")) {
                        registered.set(((ChannelIdentifier[]) args[0])[0]);
                    }
                    return null;
                }
        );
        return (ProxyServer) Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAllServers" -> servers;
                    case "getChannelRegistrar" -> registrar;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Logger logger() {
        return (Logger) Proxy.newProxyInstance(
                Logger.class.getClassLoader(),
                new Class<?>[]{Logger.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }
}
