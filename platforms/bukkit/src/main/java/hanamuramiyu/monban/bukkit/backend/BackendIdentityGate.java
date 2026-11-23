package hanamuramiyu.monban.bukkit.backend;

/**
 * Refuses to start backend permissions when monban cannot trust the source of the player identity.
 *
 * <p>The gate does not log. The caller owns log levels and messages so that a refusal is reported once,
 * by the startup failure handler.
 */
public final class BackendIdentityGate {
    private static final String UNVERIFIED_FORWARDING_MESSAGE = """
            backend-permissions.enabled requires player identity that comes from a trusted proxy,
            but monban cannot verify proxy-authenticated forwarding on this server.
            The Bukkit/Spigot build has no way to confirm that this server is reachable only from the proxy.

            Choose one:
              1) run the monban-paper-folia build on Paper with Velocity modern forwarding enabled (monban can verify that), or
              2) keep this server's port reachable only from Velocity and set backend-permissions.allow-unverified-identity: true
                 in plugins/monban/config.yml, or
              3) disable backend-permissions on this server to use standalone monban whitelist enforcement.

            monban refuses to apply permissions from an identity it cannot trust.
            """;

    private static final String LEGACY_FORWARDING_MESSAGE = """
            backend-permissions.enabled requires player identity that comes from a trusted proxy,
            but this server accepts an unauthenticated player UUID from the connecting client.
            settings.bungeecord: true in spigot.yml makes the server read the player UUID from the connection handshake.
            Legacy BungeeCord forwarding has no shared secret, so a direct connection can present any UUID.

            Choose one:
              1) move this backend to the monban-paper-folia build on Paper with Velocity modern forwarding enabled, or
              2) keep this server's port reachable only from the proxy and set
                 backend-permissions.allow-unverified-identity: true in plugins/monban/config.yml, or
              3) disable backend-permissions on this server to use standalone monban whitelist enforcement.

            monban refuses to apply permissions from an identity it cannot trust.
            """;

    private BackendIdentityGate() {
    }

    /**
     * @throws IllegalStateException if backend permissions are enabled, the identity source is not
     *                               confirmed and the operator did not allow unverified identities
     */
    public static void requireTrustedBackendIdentity(
            boolean backendPermissionsEnabled,
            ProxyForwardingStatus forwardingStatus,
            boolean allowUnverifiedIdentity
    ) {
        if (!backendPermissionsEnabled) {
            return;
        }
        if (forwardingStatus == ProxyForwardingStatus.AUTHENTICATED_PROXY) {
            return;
        }
        if (allowUnverifiedIdentity) {
            return;
        }
        throw new IllegalStateException(messageFor(forwardingStatus));
    }

    private static String messageFor(ProxyForwardingStatus forwardingStatus) {
        if (forwardingStatus == ProxyForwardingStatus.LEGACY_BUNGEE_FORWARDING) {
            return LEGACY_FORWARDING_MESSAGE;
        }
        return UNVERIFIED_FORWARDING_MESSAGE;
    }
}
