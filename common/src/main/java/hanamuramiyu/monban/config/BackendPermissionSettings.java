package hanamuramiyu.monban.config;

import java.util.Objects;
import java.util.Optional;

public record BackendPermissionSettings(
        boolean enabled,
        Optional<String> serverName,
        boolean allowUnverifiedIdentity
) {
    public BackendPermissionSettings(boolean enabled, Optional<String> serverName) {
        this(enabled, serverName, false);
    }

    public BackendPermissionSettings {
        Objects.requireNonNull(serverName, "serverName");
        serverName = serverName.map(BackendPermissionSettings::requireServerName);
    }

    public static BackendPermissionSettings defaults() {
        return new BackendPermissionSettings(false, Optional.empty(), false);
    }

    public BackendPermissionSettings withServerName(String value) {
        return new BackendPermissionSettings(enabled, Optional.of(value), allowUnverifiedIdentity);
    }

    public BackendPermissionSettings withAllowUnverifiedIdentity(boolean value) {
        return new BackendPermissionSettings(enabled, serverName, value);
    }

    private static String requireServerName(String value) {
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException("Backend server name must be clean text: " + value);
        }
        return value;
    }
}
