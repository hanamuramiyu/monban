package hanamuramiyu.monban.bukkit.backend;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classifies how this server receives player identities.
 *
 * <p>The detector only reports what the platform configuration states. It never proves network isolation
 * and never throws: any unexpected condition is reported as {@link ProxyForwardingStatus#UNDETERMINED}.
 */
public final class BukkitProxyForwardingDetector {
    private static final Logger LOGGER = Logger.getLogger(BukkitProxyForwardingDetector.class.getName());
    private static final String PAPER_GLOBAL_CONFIGURATION = "io.papermc.paper.configuration.GlobalConfiguration";
    private static final String LEGACY_FORWARDING_SETTING = "settings.bungeecord";

    private BukkitProxyForwardingDetector() {
    }

    /**
     * Never throws.
     *
     * <p>Both forwarding signals are read; modern forwarding alone does not short-circuit the legacy probe.
     * When modern and legacy forwarding are enabled at the same time it is not proven that Paper rejects the
     * legacy handshake, so the combination is reported as {@link ProxyForwardingStatus#LEGACY_BUNGEE_FORWARDING}
     * and requires the explicit opt-in.
     */
    public static ProxyForwardingStatus detect(Server server) {
        if (server == null) {
            return ProxyForwardingStatus.UNDETERMINED;
        }
        boolean paperVelocityForwarding = isPaperVelocityForwardingEnabled();
        Boolean legacyForwarding = readLegacyBungeeForwarding(server);
        if (legacyForwarding == null) {
            return ProxyForwardingStatus.UNDETERMINED;
        }
        return classify(paperVelocityForwarding, legacyForwarding);
    }

    static ProxyForwardingStatus classify(boolean paperVelocityForwarding, boolean legacyBungeeForwarding) {
        if (paperVelocityForwarding && legacyBungeeForwarding) {
            return ProxyForwardingStatus.LEGACY_BUNGEE_FORWARDING;
        }
        if (paperVelocityForwarding) {
            return ProxyForwardingStatus.AUTHENTICATED_PROXY;
        }
        if (legacyBungeeForwarding) {
            return ProxyForwardingStatus.LEGACY_BUNGEE_FORWARDING;
        }
        return ProxyForwardingStatus.NO_FORWARDING;
    }

    private static boolean isPaperVelocityForwardingEnabled() {
        try {
            Class<?> globalConfiguration = Class.forName(PAPER_GLOBAL_CONFIGURATION);
            Method get = globalConfiguration.getMethod("get");
            Object configuration = get.invoke(null);
            Object proxies = field(configuration, "proxies");
            Object velocity = field(proxies, "velocity");
            Object enabled = field(velocity, "enabled");
            return enabled instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            LOGGER.log(
                    Level.FINE,
                    "Could not read proxies.velocity.enabled; treating Velocity forwarding as unconfirmed.",
                    exception
            );
            return false;
        }
    }

    private static Boolean readLegacyBungeeForwarding(Server server) {
        try {
            Server.Spigot spigot = server.spigot();
            if (spigot == null) {
                LOGGER.log(Level.FINE, "server.spigot() is unavailable; treating forwarding as undetermined.");
                return null;
            }
            YamlConfiguration configuration = spigot.getConfig();
            if (configuration == null) {
                LOGGER.log(Level.FINE, "spigot.yml is unavailable; treating forwarding as undetermined.");
                return null;
            }
            return configuration.getBoolean(LEGACY_FORWARDING_SETTING, false);
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.log(
                    Level.FINE,
                    "Could not read " + LEGACY_FORWARDING_SETTING + "; treating forwarding as undetermined.",
                    exception
            );
            return null;
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        if (target == null) {
            throw new NoSuchFieldException(name);
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
