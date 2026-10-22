package hanamuramiyu.monban.velocity.floodgate;

import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class VelocityFloodgateIdentityResolver {
    private final VelocityFloodgateProvider provider;

    public VelocityFloodgateIdentityResolver(VelocityFloodgateProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    public Optional<PlayerIdentity> resolveFloodgate(String name, UUID connectionUuid) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(connectionUuid, "connectionUuid");
        Optional<FloodgatePlayer> player = provider.find(connectionUuid);
        return player.map(value -> PlayerIdentity.floodgate(value.name(), value.xuid()));
    }

    public FloodgateLoginResolution loginResolution(boolean requireProvider) {
        return new FloodgateLoginResolution(provider, requireProvider);
    }

    public PlayerIdentity resolve(String name, UUID connectionUuid, boolean authenticated,
                                  java.util.function.BiFunction<String, UUID, PlayerIdentity> javaResolver) {
        return new FloodgateLoginResolution(provider, false)
                .resolve(name, connectionUuid, authenticated, javaResolver);
    }
}
