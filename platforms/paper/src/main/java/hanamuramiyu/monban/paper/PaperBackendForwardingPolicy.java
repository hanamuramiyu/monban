package hanamuramiyu.monban.paper;

/**
 * Refuses to start Paper backend permissions when the forwarded identity source is not trustworthy.
 *
 * <p>Velocity modern forwarding is verifiable from the Paper configuration. Legacy
 * {@code settings.bungeecord: true} is not: it lets the server read an unauthenticated UUID from the
 * connection handshake, so a direct connection can present any UUID. Paper must not accept the legacy
 * handshake while modern forwarding is enabled, therefore the combination is refused without an opt-in.
 *
 * <p>The gate does not log. The caller owns log levels and messages so that a refusal is reported once,
 * by the existing startup failure handler.
 */
final class PaperBackendForwardingPolicy {
    private static final String VELOCITY_FORWARDING_REQUIRED_MESSAGE =
            "backend-permissions.enabled requires Paper Velocity forwarding to be enabled.";

    private static final String DUAL_FORWARDING_MESSAGE = """
            backend-permissions.enabled requires Paper Velocity modern forwarding without legacy BungeeCord forwarding,
            but settings.bungeecord: true is still set in spigot.yml.
            Paper must not accept the legacy BungeeCord handshake while Velocity modern forwarding is enabled,
            so the combination cannot be trusted and a direct connection can present any UUID.

            Remove settings.bungeecord: true from spigot.yml and restart the server.

            monban refuses to apply permissions from an identity it cannot trust.
            """;

    private static final String UNVERIFIED_LEGACY_FORWARDING_MESSAGE = """
            backend-permissions.enabled requires Paper Velocity modern forwarding without legacy BungeeCord forwarding,
            but monban could not read settings.bungeecord from spigot.yml and cannot exclude legacy forwarding.

            Make spigot.yml readable and set settings.bungeecord: false, then restart the server.

            monban refuses to apply permissions from an identity it cannot trust.
            """;

    private PaperBackendForwardingPolicy() {
    }

    /**
     * @param legacyBungeeForwarding the legacy forwarding setting, or {@code null} when it could not be read
     * @throws IllegalStateException if backend permissions are enabled and the forwarding configuration
     *                               cannot be trusted
     */
    static void requireTrustedForwarding(
            boolean backendPermissionsEnabled,
            boolean velocityForwardingEnabled,
            Boolean legacyBungeeForwarding
    ) {
        if (!backendPermissionsEnabled) {
            return;
        }
        if (!velocityForwardingEnabled) {
            throw new IllegalStateException(VELOCITY_FORWARDING_REQUIRED_MESSAGE);
        }
        if (legacyBungeeForwarding == null) {
            throw new IllegalStateException(UNVERIFIED_LEGACY_FORWARDING_MESSAGE);
        }
        if (legacyBungeeForwarding) {
            throw new IllegalStateException(DUAL_FORWARDING_MESSAGE);
        }
    }
}
