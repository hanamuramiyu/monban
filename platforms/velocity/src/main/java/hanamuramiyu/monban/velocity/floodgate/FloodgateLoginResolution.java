package hanamuramiyu.monban.velocity.floodgate;

import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

public final class FloodgateLoginResolution {
    private final VelocityFloodgateProvider provider;
    private final boolean requireProvider;

    public FloodgateLoginResolution(VelocityFloodgateProvider provider, boolean requireProvider) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.requireProvider = requireProvider;
    }

    public FloodgateLoginResolution(VelocityFloodgateProvider provider) {
        this(provider, false);
    }

    public PlayerIdentity resolve(
            String username,
            UUID connectionUuid,
            boolean platformAuthenticated,
            BiFunction<String, UUID, PlayerIdentity> javaResolver
    ) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(connectionUuid, "connectionUuid");
        Objects.requireNonNull(javaResolver, "javaResolver");

        if (requireProvider && (!provider.available() || provider.failed())) {
            throw new FloodgateUnavailableException("Floodgate provider is required but unavailable.");
        }

        Optional<FloodgatePlayer> floodgate = provider.find(connectionUuid);
        if (provider.failed()) {
            throw new FloodgateUnavailableException("Floodgate provider failed while resolving a login.");
        }
        if (floodgate.isPresent()) {
            return PlayerIdentity.floodgate(floodgate.orElseThrow().name(), floodgate.orElseThrow().xuid());
        }
        return javaResolver.apply(username, connectionUuid);
    }

    public PlayerIdentity resolve(
            String username,
            UUID connectionUuid,
            boolean platformAuthenticated,
            hanamuramiyu.monban.identity.PlayerIdentityResolver javaResolver
    ) {
        Objects.requireNonNull(javaResolver, "javaResolver");
        return resolve(username, connectionUuid, platformAuthenticated,
                (name, uuid) -> javaResolver.resolve(name, uuid, platformAuthenticated));
    }

    public boolean providerAvailable() {
        return provider.available() && !provider.failed();
    }

    public boolean requireProvider() {
        return requireProvider;
    }
}
