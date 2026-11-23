package hanamuramiyu.monban.velocity;

import java.util.Objects;

final class VelocityCompatibilityGuard {
    static final String MINIMUM_VELOCITY_VERSION = "3.5.1";

    private static final String REQUIRED_LOGIN_METHOD = "getServerIdHash";

    private VelocityCompatibilityGuard() {
    }

    static void requireSupportedLoginApi(Class<?> loginEventClass) {
        Objects.requireNonNull(loginEventClass, "loginEventClass");

        try {
            loginEventClass.getMethod(REQUIRED_LOGIN_METHOD);
        } catch (NoSuchMethodException exception) {
            throw unsupportedVelocity();
        }
    }

    private static IllegalStateException unsupportedVelocity() {
        return new IllegalStateException(
                "monban " + MonbanVelocityPluginMetadata.VERSION
                        + " requires Velocity " + MINIMUM_VELOCITY_VERSION + " or newer: "
                        + "LoginEvent.getServerIdHash() is not available on this proxy. "
                        + "Update Velocity to " + MINIMUM_VELOCITY_VERSION + " or newer before installing monban."
        );
    }
}
