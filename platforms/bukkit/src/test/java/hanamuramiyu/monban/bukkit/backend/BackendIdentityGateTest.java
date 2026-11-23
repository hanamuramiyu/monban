package hanamuramiyu.monban.bukkit.backend;

import org.junit.jupiter.api.Test;

import java.util.List;

import static hanamuramiyu.monban.bukkit.backend.ProxyForwardingStatus.AUTHENTICATED_PROXY;
import static hanamuramiyu.monban.bukkit.backend.ProxyForwardingStatus.LEGACY_BUNGEE_FORWARDING;
import static hanamuramiyu.monban.bukkit.backend.ProxyForwardingStatus.NO_FORWARDING;
import static hanamuramiyu.monban.bukkit.backend.ProxyForwardingStatus.NOT_APPLICABLE;
import static hanamuramiyu.monban.bukkit.backend.ProxyForwardingStatus.UNDETERMINED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendIdentityGateTest {
    @Test
    void refusesStartWhenForwardingIsNotDeclaredAndIdentityIsUnverified() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> BackendIdentityGate.requireTrustedBackendIdentity(true, NO_FORWARDING, false)
        );

        assertTrue(failure.getMessage().contains("allow-unverified-identity"));
        assertTrue(failure.getMessage().contains("backend-permissions"));
        assertTrue(failure.getMessage().contains("trusted proxy"));
        assertFalse(failure.getMessage().contains("no shared secret"));
    }

    @Test
    void refusesStartWithLegacyForwardingExplanationWhenBungeeForwardingIsEnabled() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> BackendIdentityGate.requireTrustedBackendIdentity(true, LEGACY_BUNGEE_FORWARDING, false)
        );

        assertTrue(failure.getMessage().contains("bungeecord"));
        assertTrue(failure.getMessage().contains("allow-unverified-identity"));
        assertTrue(failure.getMessage().contains("no shared secret"));
    }

    @Test
    void refusesStartWhenForwardingIsUndetermined() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> BackendIdentityGate.requireTrustedBackendIdentity(true, UNDETERMINED, false)
        );

        assertTrue(failure.getMessage().contains("allow-unverified-identity"));
        assertFalse(failure.getMessage().contains("no shared secret"));
    }

    @Test
    void allowsStartWhenOperatorAllowsUnverifiedIdentity() {
        assertDoesNotThrow(() -> BackendIdentityGate.requireTrustedBackendIdentity(true, NO_FORWARDING, true));
        assertDoesNotThrow(() -> BackendIdentityGate.requireTrustedBackendIdentity(true, UNDETERMINED, true));
        assertDoesNotThrow(
                () -> BackendIdentityGate.requireTrustedBackendIdentity(true, LEGACY_BUNGEE_FORWARDING, true)
        );
    }

    @Test
    void allowsStartWhenProxyForwardingIsAuthenticated() {
        assertDoesNotThrow(() -> BackendIdentityGate.requireTrustedBackendIdentity(true, AUTHENTICATED_PROXY, false));
        assertDoesNotThrow(() -> BackendIdentityGate.requireTrustedBackendIdentity(true, AUTHENTICATED_PROXY, true));
    }

    @Test
    void ignoresForwardingStatusWhenBackendPermissionsAreDisabled() {
        for (ProxyForwardingStatus status : List.of(NO_FORWARDING, LEGACY_BUNGEE_FORWARDING, UNDETERMINED)) {
            assertDoesNotThrow(
                    () -> BackendIdentityGate.requireTrustedBackendIdentity(false, status, false),
                    "disabled backend permissions should not gate on " + status
            );
        }
    }

    @Test
    void ignoresNotApplicableStatusWhenBackendPermissionsAreDisabled() {
        assertDoesNotThrow(
                () -> BackendIdentityGate.requireTrustedBackendIdentity(false, NOT_APPLICABLE, false)
        );
    }

    @Test
    void refusesNotApplicableStatusWhenBackendPermissionsAreEnabled() {
        // Defensive branch: NOT_APPLICABLE is only produced when backend permissions are disabled, so
        // reaching the gate with it means the caller is inconsistent. The gate must fail closed.
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> BackendIdentityGate.requireTrustedBackendIdentity(true, NOT_APPLICABLE, false)
        );

        assertTrue(failure.getMessage().contains("allow-unverified-identity"));
        assertFalse(failure.getMessage().contains("no shared secret"));
    }
}
