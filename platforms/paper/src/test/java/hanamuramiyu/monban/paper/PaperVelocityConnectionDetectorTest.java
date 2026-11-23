package hanamuramiyu.monban.paper;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaperVelocityConnectionDetectorTest {
    @Test
    void legacyForwardingSettingIsTheServerConfigurationKey() {
        // The key is a contract with spigot.yml; pinning the literal catches a typo in the constant that
        // the behavioural tests below cannot see because they build the fake configuration from it.
        assertEquals("settings.bungeecord", PaperVelocityConnectionDetector.LEGACY_FORWARDING_SETTING);
    }

    @Test
    void legacyForwardingIsReportedWhenSpigotConfigEnablesIt() {
        Server server = server(new FakeSpigot(spigotConfig(true), null));

        assertEquals(Boolean.TRUE, PaperVelocityConnectionDetector.isLegacyBungeeForwardingEnabled(server));
    }

    @Test
    void legacyForwardingIsReportedOffWhenTheSettingIsExplicitlyDisabled() {
        Server server = server(new FakeSpigot(spigotConfig(false), null));

        assertEquals(Boolean.FALSE, PaperVelocityConnectionDetector.isLegacyBungeeForwardingEnabled(server));
    }

    @Test
    void legacyForwardingIsReportedOffWhenTheSettingIsAbsent() {
        Server server = server(new FakeSpigot(new YamlConfiguration(), null));

        assertEquals(Boolean.FALSE, PaperVelocityConnectionDetector.isLegacyBungeeForwardingEnabled(server));
    }

    @Test
    void legacyForwardingIsUnknownWhenSpigotConfigurationIsUnavailable() {
        assertNull(PaperVelocityConnectionDetector.isLegacyBungeeForwardingEnabled(server(new FakeSpigot(null, null))));
        assertNull(PaperVelocityConnectionDetector.isLegacyBungeeForwardingEnabled(server(null)));
        assertNull(PaperVelocityConnectionDetector.isLegacyBungeeForwardingEnabled(null));
    }

    @Test
    void legacyForwardingIsUnknownWhenTheSpigotProbeFails() {
        Server server = server(new FakeSpigot(null, new IllegalStateException("spigot is unavailable")));

        assertNull(PaperVelocityConnectionDetector.isLegacyBungeeForwardingEnabled(server));
    }

    @Test
    void legacyForwardingIsUnknownWhenTheSpigotProbeThrowsALinkageError() {
        Server server = server(new FakeSpigot(null, new NoClassDefFoundError("spigot.yml support is missing")));

        assertNull(PaperVelocityConnectionDetector.isLegacyBungeeForwardingEnabled(server));
    }

    @Test
    void velocityProbeDoesNotFailWhenPaperServerConfigurationIsAbsent() {
        // No Paper server classes on this classpath, so the reflection probe must degrade to "not confirmed".
        assertFalse(PaperVelocityConnectionDetector.isVelocityEnabled(server(new FakeSpigot(spigotConfig(false), null))));
        assertFalse(PaperVelocityConnectionDetector.isVelocityEnabled(null));
    }

    private static YamlConfiguration spigotConfig(boolean bungeeCordForwarding) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set(PaperVelocityConnectionDetector.LEGACY_FORWARDING_SETTING, bungeeCordForwarding);
        return configuration;
    }

    private static Server server(Server.Spigot spigot) {
        return (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "spigot" -> spigot;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class FakeSpigot extends Server.Spigot {
        private final YamlConfiguration configuration;
        private final Throwable failure;

        private FakeSpigot(YamlConfiguration configuration, Throwable failure) {
            this.configuration = configuration;
            this.failure = failure;
        }

        @Override
        public YamlConfiguration getConfig() {
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            return configuration;
        }
    }
}
