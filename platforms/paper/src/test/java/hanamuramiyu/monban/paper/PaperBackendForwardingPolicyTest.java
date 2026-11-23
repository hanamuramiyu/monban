package hanamuramiyu.monban.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperBackendForwardingPolicyTest {
    @Test
    void skipsTheCheckWhenBackendPermissionsAreDisabled() {
        for (boolean velocityForwardingEnabled : new boolean[]{false, true}) {
            for (Boolean legacyForwarding : new Boolean[]{null, false, true}) {
                assertDoesNotThrow(
                        () -> PaperBackendForwardingPolicy.requireTrustedForwarding(
                                false,
                                velocityForwardingEnabled,
                                legacyForwarding
                        ),
                        "disabled backend permissions must skip the check for velocity="
                                + velocityForwardingEnabled
                                + ", legacy="
                                + legacyForwarding
                );
            }
        }
    }

    @Test
    void refusesStartWhenVelocityForwardingIsDisabledEvenIfLegacyForwardingIsEnabled() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> PaperBackendForwardingPolicy.requireTrustedForwarding(true, false, true)
        );

        assertTrue(exception.getMessage().contains("requires Paper Velocity forwarding to be enabled"));
        assertFalse(
                exception.getMessage().contains("settings.bungeecord: true is still set"),
                "legacy forwarding must not replace the missing Velocity forwarding explanation"
        );
    }

    @Test
    void refusesStartWhenVelocityForwardingIsDisabled() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> PaperBackendForwardingPolicy.requireTrustedForwarding(true, false, false)
        );

        assertTrue(exception.getMessage().contains("requires Paper Velocity forwarding to be enabled"));
    }

    @Test
    void refusesStartWithVelocityMessageWhenVelocityForwardingIsDisabledAndLegacyIsUnknown() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> PaperBackendForwardingPolicy.requireTrustedForwarding(true, false, null)
        );

        assertTrue(exception.getMessage().contains("requires Paper Velocity forwarding to be enabled"));
        assertFalse(exception.getMessage().contains("could not read settings.bungeecord"));
    }

    @Test
    void allowsStartWithVelocityModernForwardingWithoutLegacyForwarding() {
        assertDoesNotThrow(() -> PaperBackendForwardingPolicy.requireTrustedForwarding(true, true, false));
    }

    @Test
    void refusesStartWhenVelocityAndLegacyForwardingAreEnabledTogether() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> PaperBackendForwardingPolicy.requireTrustedForwarding(true, true, true)
        );

        assertTrue(exception.getMessage().contains("settings.bungeecord: true"));
        assertTrue(exception.getMessage().contains("Remove settings.bungeecord: true"));
        assertFalse(exception.getMessage().contains("could not read settings.bungeecord"));
    }

    @Test
    void refusesStartFailClosedWhenLegacyForwardingIsUnknown() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> PaperBackendForwardingPolicy.requireTrustedForwarding(true, true, null)
        );

        assertTrue(exception.getMessage().contains("could not read settings.bungeecord"));
        assertFalse(exception.getMessage().contains("settings.bungeecord: true is still set"));
    }
}
