package hanamuramiyu.monban.velocity;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloodgateStartupDiagnosticsTest {
    @Test
    void warnsWhenFloodgateEnabledWithoutProviderAndRequireProviderDisabled() {
        Optional<String> warning = FloodgateStartupDiagnostics.missingProviderWarning(true, false, false);

        assertTrue(warning.isPresent());
        assertTrue(warning.orElseThrow().contains("require-provider"));
        assertTrue(warning.orElseThrow().contains("Floodgate API is unavailable"));
    }

    @Test
    void doesNotWarnWhenProviderAvailable() {
        assertFalse(FloodgateStartupDiagnostics.missingProviderWarning(true, false, true).isPresent());
    }

    @Test
    void doesNotWarnWhenRequireProviderEnabled() {
        assertFalse(FloodgateStartupDiagnostics.missingProviderWarning(true, true, false).isPresent());
    }

    @Test
    void doesNotWarnWhenFloodgateDisabled() {
        assertFalse(FloodgateStartupDiagnostics.missingProviderWarning(false, false, false).isPresent());
        assertFalse(FloodgateStartupDiagnostics.missingProviderWarning(false, true, false).isPresent());
    }
}
