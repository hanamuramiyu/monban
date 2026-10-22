package hanamuramiyu.monban.velocity.floodgate;

import java.util.Optional;
import java.util.UUID;

public interface VelocityFloodgateProvider {
    Optional<FloodgatePlayer> find(UUID connectionUuid);

    default boolean available() { return true; }

    default boolean failed() { return false; }
}
