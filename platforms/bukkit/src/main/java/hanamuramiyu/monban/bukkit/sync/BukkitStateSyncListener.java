package hanamuramiyu.monban.bukkit.sync;

import hanamuramiyu.monban.sync.PlayerAccessStateReceiver;
import hanamuramiyu.monban.sync.RateLimitedLogger;
import hanamuramiyu.monban.sync.SyncAcceptance;
import hanamuramiyu.monban.sync.SyncChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BukkitStateSyncListener implements PluginMessageListener, Listener {
    private final PlayerAccessStateReceiver receiver;
    private final Runnable stateChanged;
    private final Consumer<String> debugLog;
    private final RateLimitedLogger warnLog;

    public BukkitStateSyncListener(PlayerAccessStateReceiver receiver, Runnable stateChanged, Logger logger) {
        this(receiver, stateChanged, debugSink(logger), warnLogger(logger));
    }

    private static Consumer<String> debugSink(Logger logger) {
        Objects.requireNonNull(logger, "logger");
        return message -> logger.log(Level.FINE, message);
    }

    private static RateLimitedLogger warnLogger(Logger logger) {
        Objects.requireNonNull(logger, "logger");
        return new RateLimitedLogger(logger::warning, System::nanoTime);
    }

    public BukkitStateSyncListener(
            PlayerAccessStateReceiver receiver,
            Runnable stateChanged,
            Consumer<String> debugLog,
            RateLimitedLogger warnLog
    ) {
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.stateChanged = Objects.requireNonNull(stateChanged, "stateChanged");
        this.debugLog = Objects.requireNonNull(debugLog, "debugLog");
        this.warnLog = Objects.requireNonNull(warnLog, "warnLog");
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!SyncChannel.ID.equals(channel)) {
            return;
        }
        SyncAcceptance acceptance = receiver.accept(message);
        if (acceptance.accepted()) {
            if (acceptance.idempotent()) {
                debugLog.accept(
                        "Ignored duplicate monban state snapshot [idempotent, reason=" + acceptance.reason()
                                + "]: " + acceptance.detail()
                );
                return;
            }
            stateChanged.run();
            return;
        }
        String rejection = "Rejected monban state snapshot [" + acceptance.reason() + "]: " + acceptance.detail();
        debugLog.accept(rejection);
        warnLog.warn(rejection);
    }

    public PlayerAccessStateReceiver receiver() {
        return receiver;
    }
}
