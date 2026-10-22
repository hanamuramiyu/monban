package hanamuramiyu.monban.identity;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class PlayerIdentity {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern VALID_FLOODGATE_NAME = Pattern.compile("\\S{1,16}");

    private final String name;
    private final String normalizedName;
    private final UUID technicalUuid;
    private final IdentityType type;
    private final String xuid;

    private PlayerIdentity(String name, UUID technicalUuid, IdentityType type) {
        this(name, technicalUuid, type, null);
    }

    private PlayerIdentity(String name, UUID technicalUuid, IdentityType type, String xuid) {
        this.type = Objects.requireNonNull(type, "type");
        this.name = requireValidName(name, type);
        this.normalizedName = normalizeValidName(name);
        this.technicalUuid = technicalUuid;
        this.xuid = xuid;

        if (type == IdentityType.ONLINE && technicalUuid == null) {
            throw new NullPointerException("technicalUuid");
        }
        if (type == IdentityType.FLOODGATE && xuid != null && !xuid.matches("[0-9]{1,32}")) {
            throw new IllegalArgumentException("Invalid Floodgate XUID");
        }
    }

    public static PlayerIdentity online(String name, UUID verifiedUuid) {
        return new PlayerIdentity(name, Objects.requireNonNull(verifiedUuid, "verifiedUuid"), IdentityType.ONLINE);
    }

    public static PlayerIdentity offline(String name) {
        return new PlayerIdentity(name, null, IdentityType.OFFLINE);
    }

    public static PlayerIdentity offline(String name, UUID technicalUuid) {
        return new PlayerIdentity(name, Objects.requireNonNull(technicalUuid, "technicalUuid"), IdentityType.OFFLINE);
    }

    public static PlayerIdentity floodgate(String name, String xuid) {
        return new PlayerIdentity(name, null, IdentityType.FLOODGATE, Objects.requireNonNull(xuid, "xuid"));
    }

    public static PlayerIdentity floodgate(String name) {
        return new PlayerIdentity(name, null, IdentityType.FLOODGATE);
    }

    public String name() {
        return name;
    }

    public String normalizedName() {
        return normalizedName;
    }

    public Optional<UUID> technicalUuid() {
        return Optional.ofNullable(technicalUuid);
    }

    public Optional<UUID> verifiedUuid() {
        return type == IdentityType.ONLINE
                ? Optional.of(technicalUuid)
                : Optional.empty();
    }

    public Optional<String> xuid() { return Optional.ofNullable(xuid); }

    public boolean hasVerifiedUuid() {
        return type == IdentityType.ONLINE;
    }

    public IdentityType type() {
        return type;
    }

    public boolean matchesName(String otherName) {
        return isValidName(otherName, type) && normalizedName.equals(normalizeValidName(otherName));
    }

    public boolean sameIdentityAs(PlayerIdentity other) {
        return equals(other);
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    public static boolean isValidFloodgateName(String name) {
        return name != null && VALID_FLOODGATE_NAME.matcher(name).matches();
    }

    public static String normalizeName(String name) {
        return normalizeValidName(requireValidName(name));
    }

    private static String requireValidName(String name) {
        return requireValidName(name, IdentityType.OFFLINE);
    }

    private static String requireValidName(String name, IdentityType type) {
        Objects.requireNonNull(name, "name");
        if (!isValidName(name, type)) {
            throw new IllegalArgumentException("Invalid Minecraft player name: " + name);
        }
        return name;
    }

    private static boolean isValidName(String name, IdentityType type) {
        return type == IdentityType.FLOODGATE ? isValidFloodgateName(name) : isValidName(name);
    }

    private static String normalizeValidName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PlayerIdentity other)) {
            return false;
        }
        if (type != other.type) {
            return false;
        }
        return switch (type) {
            case ONLINE -> technicalUuid.equals(other.technicalUuid);
            case OFFLINE -> normalizedName.equals(other.normalizedName);
            case FLOODGATE -> normalizedName.equals(other.normalizedName);
        };
    }

    @Override
    public int hashCode() {
        return switch (type) {
            case ONLINE -> Objects.hash(type, technicalUuid);
            case OFFLINE -> Objects.hash(type, normalizedName);
            case FLOODGATE -> Objects.hash(type, normalizedName);
        };
    }

    @Override
    public String toString() {
        return "PlayerIdentity[name=" + name + ", technicalUuid=" + technicalUuid + ", type=" + type + "]";
    }
}
