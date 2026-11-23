package hanamuramiyu.monban.bukkit.backend;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static hanamuramiyu.monban.bukkit.backend.ProxyForwardingStatus.AUTHENTICATED_PROXY;
import static hanamuramiyu.monban.bukkit.backend.ProxyForwardingStatus.LEGACY_BUNGEE_FORWARDING;
import static hanamuramiyu.monban.bukkit.backend.ProxyForwardingStatus.NO_FORWARDING;
import static hanamuramiyu.monban.bukkit.backend.ProxyForwardingStatus.UNDETERMINED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BukkitProxyForwardingDetectorTest {
    @Test
    void classifyTreatsModernForwardingAsAuthenticatedForwarding() {
        assertEquals(AUTHENTICATED_PROXY, BukkitProxyForwardingDetector.classify(true, false));
    }

    @Test
    void classifyTreatsModernAndLegacyForwardingTogetherAsUntrusted() {
        assertEquals(LEGACY_BUNGEE_FORWARDING, BukkitProxyForwardingDetector.classify(true, true));
    }

    @Test
    void classifyTreatsLegacyBungeeForwardingAsUnauthenticated() {
        assertEquals(LEGACY_BUNGEE_FORWARDING, BukkitProxyForwardingDetector.classify(false, true));
    }

    @Test
    void classifyWithoutForwardingSignalsReportsNoForwarding() {
        assertEquals(NO_FORWARDING, BukkitProxyForwardingDetector.classify(false, false));
    }

    @Test
    void detectReportsLegacyForwardingWhenSpigotConfigEnablesIt() {
        Server server = server(new FakeSpigot(spigotConfig(true), null));

        assertEquals(LEGACY_BUNGEE_FORWARDING, BukkitProxyForwardingDetector.detect(server));
    }

    @Test
    void detectReportsNoForwardingWhenForwardingIsNotDeclared() {
        assertEquals(
                NO_FORWARDING,
                BukkitProxyForwardingDetector.detect(server(new FakeSpigot(spigotConfig(false), null)))
        );
        assertEquals(
                NO_FORWARDING,
                BukkitProxyForwardingDetector.detect(server(new FakeSpigot(new YamlConfiguration(), null)))
        );
    }

    @Test
    void detectReportsUndeterminedWhenSpigotConfigurationIsUnavailable() {
        assertEquals(UNDETERMINED, BukkitProxyForwardingDetector.detect(server(new FakeSpigot(null, null))));
        assertEquals(UNDETERMINED, BukkitProxyForwardingDetector.detect(server(null)));
        assertEquals(UNDETERMINED, BukkitProxyForwardingDetector.detect(null));
    }

    @Test
    void detectReportsUndeterminedWhenSpigotProbeFails() {
        Server server = server(new FakeSpigot(null, new IllegalStateException("spigot is unavailable")));

        assertEquals(UNDETERMINED, BukkitProxyForwardingDetector.detect(server));
    }

    @Test
    void detectReportsUndeterminedWhenSpigotProbeThrowsALinkageError() {
        Server server = server(new FakeSpigot(null, new NoClassDefFoundError("spigot.yml support is missing")));

        assertEquals(UNDETERMINED, BukkitProxyForwardingDetector.detect(server));
    }

    @Test
    void detectDoesNotReportAuthenticatedProxyWithoutPaperConfiguration() {
        // No Paper classes on this classpath, so only legacy/no-forwarding outcomes are possible.
        Server legacyServer = server(new FakeSpigot(spigotConfig(true), null));
        assertEquals(LEGACY_BUNGEE_FORWARDING, BukkitProxyForwardingDetector.detect(legacyServer));

        Server plainServer = server(new FakeSpigot(spigotConfig(false), null));
        assertEquals(NO_FORWARDING, BukkitProxyForwardingDetector.detect(plainServer));
    }

    private static YamlConfiguration spigotConfig(boolean bungeeCordForwarding) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("settings.bungeecord", bungeeCordForwarding);
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
