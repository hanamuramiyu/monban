package hanamuramiyu.monban.velocity.sync;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantInventory;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.PlayerGroupRepository;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrantRepository;
import hanamuramiyu.monban.storage.file.sync.FileSyncEpochStore;
import hanamuramiyu.monban.sync.PlayerAccessStateCodec;
import hanamuramiyu.monban.sync.PlayerAccessStateSnapshot;
import hanamuramiyu.monban.sync.RateLimitedLogger;
import hanamuramiyu.monban.sync.SyncChannel;
import hanamuramiyu.monban.sync.SyncSecret;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicLong;

public final class VelocityStateSynchronizer {
    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(SyncChannel.ID);

    private final ProxyServer server;
    private final AccessGrantInventory accessGrants;
    private final PlayerGroupRepository groups;
    private final PlayerGroupAssignmentRepository assignments;
    private final PlayerPermissionGrantRepository permissions;
    private final SyncSecret secret;
    private final Logger logger;
    private final StateRevisionStore revisionStore;
    private final FileSyncEpochStore epochStore;
    private final BooleanSupplier networkWhitelistEnabled;
    private final RateLimitedLogger revisionWarnLogger;
    private final PlayerAccessStateCodec codec = new PlayerAccessStateCodec();
    private final UUID epoch;
    private final AtomicLong revision;
    /** Fingerprint of the last published state; guarded by the {@link #snapshot()} monitor. */
    private byte[] publishedFingerprint;
    /** A previous revision write failed, so the next snapshot must bump and retry. */
    private boolean persistPending;

    public VelocityStateSynchronizer(
            ProxyServer server,
            AccessGrantInventory accessGrants,
            PlayerGroupRepository groups,
            PlayerGroupAssignmentRepository assignments,
            PlayerPermissionGrantRepository permissions,
            SyncSecret secret,
            Logger logger
    ) {
        this(server, accessGrants, groups, assignments, permissions, secret, logger, (Path) null);
    }

    public VelocityStateSynchronizer(
            ProxyServer server,
            AccessGrantInventory accessGrants,
            PlayerGroupRepository groups,
            PlayerGroupAssignmentRepository assignments,
            PlayerPermissionGrantRepository permissions,
            SyncSecret secret,
            Logger logger,
            Path revisionFile
    ) {
        this(
                server,
                accessGrants,
                groups,
                assignments,
                permissions,
                secret,
                logger,
                revisionFile == null ? null : new VelocityStateRevisionStore(revisionFile),
                null,
                () -> true,
                new RateLimitedLogger(logger::warn, System::nanoTime)
        );
    }

    public VelocityStateSynchronizer(
            ProxyServer server,
            AccessGrantInventory accessGrants,
            PlayerGroupRepository groups,
            PlayerGroupAssignmentRepository assignments,
            PlayerPermissionGrantRepository permissions,
            SyncSecret secret,
            Logger logger,
            Path revisionFile,
            BooleanSupplier networkWhitelistEnabled,
            FileSyncEpochStore epochStore
    ) {
        this(
                server,
                accessGrants,
                groups,
                assignments,
                permissions,
                secret,
                logger,
                revisionFile == null ? null : new VelocityStateRevisionStore(revisionFile),
                epochStore,
                networkWhitelistEnabled,
                new RateLimitedLogger(logger::warn, System::nanoTime)
        );
    }

    VelocityStateSynchronizer(
            ProxyServer server,
            AccessGrantInventory accessGrants,
            PlayerGroupRepository groups,
            PlayerGroupAssignmentRepository assignments,
            PlayerPermissionGrantRepository permissions,
            SyncSecret secret,
            Logger logger,
            StateRevisionStore revisionStore,
            FileSyncEpochStore epochStore,
            BooleanSupplier networkWhitelistEnabled,
            RateLimitedLogger revisionWarnLogger
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.accessGrants = Objects.requireNonNull(accessGrants, "accessGrants");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.assignments = Objects.requireNonNull(assignments, "assignments");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.secret = Objects.requireNonNull(secret, "secret");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.revisionStore = revisionStore;
        this.epochStore = epochStore;
        this.networkWhitelistEnabled = Objects.requireNonNull(networkWhitelistEnabled, "networkWhitelistEnabled");
        this.revisionWarnLogger = Objects.requireNonNull(revisionWarnLogger, "revisionWarnLogger");
        Lineage lineage = initLineage();
        this.epoch = lineage.epoch();
        this.revision = new AtomicLong(lineage.revision());
    }

    public void registerChannel() {
        server.getChannelRegistrar().register(CHANNEL);
    }

    public void unregisterChannel() {
        server.getChannelRegistrar().unregister(CHANNEL);
    }

    public void broadcast() {
        byte[] payload = codec.encode(snapshot(), secret);
        for (RegisteredServer registeredServer : server.getAllServers()) {
            send(registeredServer, payload);
        }
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        event.getInitialServer().ifPresent(registeredServer -> send(registeredServer, codec.encode(snapshot(), secret)));
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        event.getPlayer().getCurrentServer()
                .map(connection -> connection.getServer())
                .ifPresent(registeredServer -> send(registeredServer, codec.encode(snapshot(), secret)));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (CHANNEL.equals(event.getIdentifier()) && event.getSource() instanceof Player) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        }
    }

    /**
     * The epoch and the state revision form one lineage. Losing either file recreates
     * both: a fresh epoch starts revisions from zero so backends accept the new lineage
     * immediately. A corrupted file fails startup (fail-closed). The epoch is persisted
     * before the first snapshot is sent.
     */
    private Lineage initLineage() {
        long storedRevision = revisionStore == null ? 0 : revisionStore.load();
        if (epochStore == null) {
            return new Lineage(UUID.randomUUID(), storedRevision);
        }
        Optional<UUID> storedEpoch = epochStore.load();
        if (storedEpoch.isPresent() && storedRevision > 0) {
            return new Lineage(storedEpoch.orElseThrow(), storedRevision);
        }
        UUID freshEpoch = UUID.randomUUID();
        epochStore.save(freshEpoch);
        return new Lineage(freshEpoch, 0);
    }

    private record Lineage(UUID epoch, long revision) {
    }

    /**
     * Builds the current snapshot and publishes it. The revision is incremented and
     * written only when the wire content changed (fingerprint), the lineage has no
     * published revision yet, or a previous revision write is still pending. A snapshot
     * is always returned (and later sent) so backends can catch up after a restart.
     */
    private synchronized PlayerAccessStateSnapshot snapshot() {
        List<AccessGrant> accessGrantList = accessGrants.findAll();
        List<PlayerGroupDefinition> groupList = groups.findAll();
        List<PlayerGroupAssignment> assignmentList = assignments.findAll();
        List<PlayerPermissionGrant> permissionList = permissions.findAll();
        boolean whitelistEnabled = networkWhitelistEnabled.getAsBoolean();

        byte[] fingerprint = codec.fingerprint(new PlayerAccessStateSnapshot(
                epoch, 1, whitelistEnabled, accessGrantList, groupList, assignmentList, permissionList));
        boolean unchanged = !persistPending
                && revision.get() > 0
                && publishedFingerprint != null
                && Arrays.equals(fingerprint, publishedFingerprint);

        long nextRevision = unchanged ? revision.get() : revision.incrementAndGet();
        if (!unchanged && revisionStore != null) {
            try {
                revisionStore.save(nextRevision);
                persistPending = false;
            } catch (RuntimeException exception) {
                persistPending = true;
                revisionWarnLogger.warn(
                        "Failed to persist state revision " + nextRevision + ": " + exception.getMessage()
                                + (exception.getCause() == null ? "" : " (" + exception.getCause().getMessage() + ")")
                );
            }
        }
        publishedFingerprint = fingerprint;

        return new PlayerAccessStateSnapshot(
                epoch,
                nextRevision,
                whitelistEnabled,
                accessGrantList,
                groupList,
                assignmentList,
                permissionList
        );
    }

    private void send(RegisteredServer registeredServer, byte[] payload) {
        if (!registeredServer.sendPluginMessage(CHANNEL, payload)) {
            logger.debug(
                    "Backend {} did not accept the monban state snapshot.",
                    registeredServer.getServerInfo().getName()
            );
        }
    }
}
