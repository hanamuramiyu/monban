package hanamuramiyu.monban.velocity;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import hanamuramiyu.monban.access.PlayerAccessService;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.config.DeploymentSettings;
import hanamuramiyu.monban.config.HybridIdentitySettings;
import hanamuramiyu.monban.config.IdentitySettings;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.config.WhitelistSettings;
import hanamuramiyu.monban.deployment.DeploymentMode;
import hanamuramiyu.monban.identity.IdentityResolutionMode;
import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.identity.PlayerIdentityResolver;
import hanamuramiyu.monban.velocity.floodgate.FloodgatePlayer;
import hanamuramiyu.monban.velocity.floodgate.VelocityFloodgateProvider;
import hanamuramiyu.monban.velocity.session.VelocityConnectionIdentityRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityPlayerLoginListenerTest {
    private static final UUID CONNECTION_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void floodgateIdentityIsPreferredAndStagedForBackendAccess() {
        PlayerIdentity floodgateIdentity = PlayerIdentity.floodgate("BedrockName");
        VelocityConnectionIdentityRegistry registry = new VelocityConnectionIdentityRegistry();
        Player player = player("VisibleJavaName", CONNECTION_UUID);
        VelocityPlayerLoginListener listener = listener(
                floodgateIdentity,
                registry,
                connectionUuid -> Optional.of(new FloodgatePlayer("BedrockName", "1234567890"))
        );
        LoginEvent event = new LoginEvent(player, "server-hash");

        run(listener.onLogin(event));

        assertTrue(event.getResult().isAllowed());
        assertEquals(floodgateIdentity, registry.activate(player).orElseThrow());
        assertEquals(IdentityType.FLOODGATE, registry.findActive(player).orElseThrow().type());
        assertEquals("1234567890", registry.findActive(player).orElseThrow().xuid().orElseThrow());
    }

    @Test
    void failedFloodgateProviderDoesNotDowngradeToJavaIdentity() {
        VelocityConnectionIdentityRegistry registry = new VelocityConnectionIdentityRegistry();
        Player player = player("JavaName", CONNECTION_UUID);
        VelocityFloodgateProvider failedProvider = new VelocityFloodgateProvider() {
            @Override
            public Optional<FloodgatePlayer> find(UUID connectionUuid) {
                return Optional.empty();
            }

            @Override
            public boolean failed() {
                return true;
            }
        };
        VelocityPlayerLoginListener listener = listener(
                PlayerIdentity.online("JavaName", CONNECTION_UUID),
                registry,
                failedProvider
        );
        LoginEvent event = new LoginEvent(player, "server-hash");

        run(listener.onLogin(event));

        assertFalse(event.getResult().isAllowed());
        assertTrue(registry.activate(player).isEmpty());
    }

    @Test
    void healthyProviderEmptyFallsBackToJavaIdentity() {
        PlayerIdentity onlineIdentity = PlayerIdentity.online("JavaName", CONNECTION_UUID);
        VelocityConnectionIdentityRegistry registry = new VelocityConnectionIdentityRegistry();
        Player player = player("JavaName", CONNECTION_UUID);
        VelocityPlayerLoginListener listener = listener(
                onlineIdentity,
                registry,
                connectionUuid -> Optional.empty()
        );
        LoginEvent event = new LoginEvent(player, "server-hash");

        run(listener.onLogin(event));

        assertTrue(event.getResult().isAllowed());
        assertEquals(onlineIdentity, registry.activate(player).orElseThrow());
    }

    private static VelocityPlayerLoginListener listener(
            PlayerIdentity grantedIdentity,
            VelocityConnectionIdentityRegistry registry,
            VelocityFloodgateProvider provider
    ) {
        InMemoryAccessGrantRepository grants = new InMemoryAccessGrantRepository();
        grants.add(new AccessGrant(AccessScope.network(), grantedIdentity));
        PlayerAccessService accessService = new PlayerAccessService(
                new MonbanConfig(
                        new DeploymentSettings(DeploymentMode.VELOCITY),
                        new WhitelistSettings(true),
                        new IdentitySettings(IdentityResolutionMode.AUTO, HybridIdentitySettings.defaults())
                ),
                new PlayerIdentityResolver(IdentityResolutionMode.AUTO),
                grants
        );
        return new VelocityPlayerLoginListener(accessService, registry, logger(), provider);
    }

    private static void run(EventTask task) {
        assertNotNull(task);
        task.execute(new Continuation() {
            @Override
            public void resume() {
            }

            @Override
            public void resumeWithException(Throwable throwable) {
                throw new AssertionError("Login task failed", throwable);
            }
        });
    }

    private static Player player(String username, UUID uuid) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUsername" -> username;
                    case "getUniqueId" -> uuid;
                    case "toString" -> "PlayerProxy[" + username + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
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
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0d;
        if (type == char.class) return '\0';
        return null;
    }
}
