package hanamuramiyu.monban.bukkit.backend;

/**
 * How this server obtains the player identity it reports to plugins.
 *
 * <p>Only {@link #AUTHENTICATED_PROXY} proves that the identity was accepted by a proxy that shares a
 * secret with this server. Every other value means monban cannot verify the source of the identity.
 */
public enum ProxyForwardingStatus {
    /** Velocity modern forwarding is enabled: the proxy signature is verified by the server. */
    AUTHENTICATED_PROXY,
    /** {@code settings.bungeecord=true}: the UUID comes from the handshake without a shared secret. */
    LEGACY_BUNGEE_FORWARDING,
    /** Forwarding is not declared, so the identity is local or offline. */
    NO_FORWARDING,
    /** The forwarding configuration could not be read. */
    UNDETERMINED,
    /** Backend permissions are disabled, so the gate does not apply. */
    NOT_APPLICABLE
}
