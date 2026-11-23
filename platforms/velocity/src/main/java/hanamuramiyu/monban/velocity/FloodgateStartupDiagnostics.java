package hanamuramiyu.monban.velocity;

import java.util.Optional;

final class FloodgateStartupDiagnostics {
    private FloodgateStartupDiagnostics() {
    }

    static Optional<String> missingProviderWarning(
            boolean floodgateEnabled,
            boolean requireProvider,
            boolean providerAvailable
    ) {
        if (!floodgateEnabled || requireProvider || providerAvailable) {
            return Optional.empty();
        }
        return Optional.of(
                "identity.floodgate.enabled is true, but the Floodgate API is unavailable. "
                        + "Bedrock players will be evaluated as Java identities and may be treated as OFFLINE. "
                        + "Install Floodgate successfully or set identity.floodgate.require-provider to true "
                        + "for fail-closed behavior."
        );
    }
}
