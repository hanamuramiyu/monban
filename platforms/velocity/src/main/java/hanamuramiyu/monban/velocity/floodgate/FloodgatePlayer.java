package hanamuramiyu.monban.velocity.floodgate;

import java.util.Objects;

public record FloodgatePlayer(String name, String xuid) {
    public FloodgatePlayer {
        Objects.requireNonNull(name, "name");
        xuid = requireValidXuid(xuid);
    }

    public static String requireValidXuid(String value) {
        Objects.requireNonNull(value, "xuid");
        if (!value.matches("[0-9]{1,32}")) {
            throw new IllegalArgumentException("XUID must be a decimal string (up to 32 digits).");
        }
        return value;
    }
}
