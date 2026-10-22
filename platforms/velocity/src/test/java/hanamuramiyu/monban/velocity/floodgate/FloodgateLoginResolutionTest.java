package hanamuramiyu.monban.velocity.floodgate;

import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloodgateLoginResolutionTest {
    private static final UUID CONNECTION_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void floodgateIdentityWinsBeforeJavaResolver() {
        AtomicBoolean javaResolverCalled = new AtomicBoolean();
        FloodgateLoginResolution resolution = new FloodgateLoginResolution(
                provider(Optional.of(new FloodgatePlayer("BedrockName", "1234567890")))
        );

        PlayerIdentity identity = resolution.resolve(
                "visible-java-name",
                CONNECTION_UUID,
                true,
                (name, uuid) -> {
                    javaResolverCalled.set(true);
                    return PlayerIdentity.online(name, uuid);
                }
        );

        assertEquals(IdentityType.FLOODGATE, identity.type());
        assertEquals("BedrockName", identity.name());
        assertEquals("1234567890", identity.xuid().orElseThrow());
        assertFalse(javaResolverCalled.get());
    }

    @Test
    void healthyEmptyProviderAllowsJavaFallback() {
        AtomicBoolean javaResolverCalled = new AtomicBoolean();
        FloodgateLoginResolution resolution = new FloodgateLoginResolution(provider(Optional.empty()));

        PlayerIdentity identity = resolution.resolve(
                "java_name",
                CONNECTION_UUID,
                true,
                (name, uuid) -> {
                    javaResolverCalled.set(true);
                    return PlayerIdentity.online(name, uuid);
                }
        );

        assertEquals(IdentityType.ONLINE, identity.type());
        assertTrue(javaResolverCalled.get());
    }

    @Test
    void providerFailureCannotDowngradeToJavaIdentity() {
        AtomicBoolean javaResolverCalled = new AtomicBoolean();
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

        assertThrows(
                FloodgateUnavailableException.class,
                () -> new FloodgateLoginResolution(failedProvider).resolve(
                        "java-name",
                        CONNECTION_UUID,
                        true,
                        (name, uuid) -> {
                            javaResolverCalled.set(true);
                            return PlayerIdentity.online(name, uuid);
                        }
                )
        );
        assertFalse(javaResolverCalled.get());
    }

    @Test
    void requiredProviderUnavailableFailsBeforeJavaIdentity() {
        AtomicBoolean javaResolverCalled = new AtomicBoolean();
        assertThrows(
                FloodgateUnavailableException.class,
                () -> new FloodgateLoginResolution(ReflectiveVelocityFloodgateProvider.unavailable(), true)
                        .resolve(
                                "java-name",
                                CONNECTION_UUID,
                                false,
                                (name, uuid) -> {
                                    javaResolverCalled.set(true);
                                    return PlayerIdentity.offline(name, uuid);
                                }
                        )
        );
        assertFalse(javaResolverCalled.get());
    }

    private static VelocityFloodgateProvider provider(Optional<FloodgatePlayer> result) {
        return connectionUuid -> result;
    }
}
