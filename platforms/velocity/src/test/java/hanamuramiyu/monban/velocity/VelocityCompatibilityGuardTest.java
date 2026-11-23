package hanamuramiyu.monban.velocity;

import com.velocitypowered.api.event.connection.LoginEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityCompatibilityGuardTest {
    @Test
    void acceptsClassWithPublicGetServerIdHash() {
        assertDoesNotThrow(() -> VelocityCompatibilityGuard.requireSupportedLoginApi(SupportedLoginEvent.class));
    }

    @Test
    void acceptsInheritedPublicGetServerIdHash() {
        assertDoesNotThrow(() -> VelocityCompatibilityGuard.requireSupportedLoginApi(InheritedSupportedLoginEvent.class));
    }

    @Test
    void acceptsLoginEventFromCompiledVelocityApi() {
        assertDoesNotThrow(() -> VelocityCompatibilityGuard.requireSupportedLoginApi(LoginEvent.class));
    }

    @Test
    void rejectsClassWithoutGetServerIdHash() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> VelocityCompatibilityGuard.requireSupportedLoginApi(UnsupportedLoginEvent.class)
        );

        assertTrue(exception.getMessage().contains("Velocity 3.5.1"));
        assertTrue(exception.getMessage().contains("LoginEvent.getServerIdHash()"));
        assertTrue(exception.getMessage().contains(MonbanVelocityPluginMetadata.VERSION));
    }

    @Test
    void rejectsNonPublicGetServerIdHash() {
        assertThrows(
                IllegalStateException.class,
                () -> VelocityCompatibilityGuard.requireSupportedLoginApi(NonPublicMethodLoginEvent.class)
        );
    }

    @Test
    void rejectsGetServerIdHashWithParameters() {
        assertThrows(
                IllegalStateException.class,
                () -> VelocityCompatibilityGuard.requireSupportedLoginApi(ParameterizedMethodLoginEvent.class)
        );
    }

    @Test
    void rejectsNullLoginEventClass() {
        assertThrows(
                NullPointerException.class,
                () -> VelocityCompatibilityGuard.requireSupportedLoginApi(null)
        );
    }

    static final class SupportedLoginEvent {
        public String getServerIdHash() {
            return "server-hash";
        }
    }

    static class BaseLoginEvent {
        public String getServerIdHash() {
            return "server-hash";
        }
    }

    static final class InheritedSupportedLoginEvent extends BaseLoginEvent {
    }

    static final class UnsupportedLoginEvent {
    }

    static final class NonPublicMethodLoginEvent {
        String getServerIdHash() {
            return "server-hash";
        }
    }

    static final class ParameterizedMethodLoginEvent {
        public String getServerIdHash(String salt) {
            return salt;
        }
    }
}
