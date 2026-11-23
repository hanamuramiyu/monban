package hanamuramiyu.monban.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsSettingsTest {
    @Test
    void defaultsEnableMetrics() {
        assertTrue(MetricsSettings.defaults().enabled());
    }

    @Test
    void explicitDisabledValueIsPreserved() {
        assertFalse(new MetricsSettings(false).enabled());
    }
}
