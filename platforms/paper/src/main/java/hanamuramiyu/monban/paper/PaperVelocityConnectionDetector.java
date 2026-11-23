package hanamuramiyu.monban.paper;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class PaperVelocityConnectionDetector {
    static final String LEGACY_FORWARDING_SETTING = "settings.bungeecord";

    private PaperVelocityConnectionDetector() {
    }

    static boolean isVelocityEnabled(Server server) {
        try {
            Class<?> globalConfiguration = Class.forName(
                    "io.papermc.paper.configuration.GlobalConfiguration"
            );
            Method get = globalConfiguration.getMethod("get");
            Object configuration = get.invoke(null);
            Object proxies = field(configuration, "proxies");
            Object velocity = field(proxies, "velocity");
            Object enabled = field(velocity, "enabled");
            return enabled instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    /**
     * Reads the legacy BungeeCord forwarding setting from {@code spigot.yml}.
     *
     * <p>Never throws. {@code null} means the setting could not be read, which the startup policy
     * treats as fail-closed instead of assuming that legacy forwarding is off.
     */
    static Boolean isLegacyBungeeForwardingEnabled(Server server) {
        if (server == null) {
            return null;
        }
        try {
            Server.Spigot spigot = server.spigot();
            if (spigot == null) {
                return null;
            }
            YamlConfiguration configuration = spigot.getConfig();
            if (configuration == null) {
                return null;
            }
            return configuration.getBoolean(LEGACY_FORWARDING_SETTING, false);
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    static boolean isVelocityOnlineMode(Server server) {
        try {
            Class<?> globalConfiguration = Class.forName(
                    "io.papermc.paper.configuration.GlobalConfiguration"
            );
            Method get = globalConfiguration.getMethod("get");
            Object configuration = get.invoke(null);
            Object proxies = field(configuration, "proxies");
            Object velocity = field(proxies, "velocity");
            Object onlineMode = field(velocity, "onlineMode");
            return onlineMode instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
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
