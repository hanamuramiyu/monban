package hanamuramiyu.monban.config;

import hanamuramiyu.monban.identity.IdentityResolutionMode;

import java.util.Objects;

public record IdentitySettings(
        IdentityResolutionMode mode,
        HybridIdentitySettings hybrid,
        FloodgateSettings floodgate
) {
    public IdentitySettings(IdentityResolutionMode mode, HybridIdentitySettings hybrid) { this(mode, hybrid, FloodgateSettings.defaults()); }
    public IdentitySettings {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(hybrid, "hybrid");
        Objects.requireNonNull(floodgate, "floodgate");
    }

    public static IdentitySettings defaults() {
        return new IdentitySettings(
                IdentityResolutionMode.AUTO,
                HybridIdentitySettings.defaults()
        );
    }
}
