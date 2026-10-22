package hanamuramiyu.monban.velocity.floodgate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public final class ReflectiveVelocityFloodgateProvider implements VelocityFloodgateProvider {
    private static final String FLOODGATE_API = "org.geysermc.floodgate.api.FloodgateApi";

    private final Object api;
    private final Method isFloodgatePlayer;
    private final Method getPlayer;
    private final boolean available;
    private volatile boolean failed;

    public ReflectiveVelocityFloodgateProvider(Object api) {
        this.api = api;
        if (api == null) {
            isFloodgatePlayer = null;
            getPlayer = null;
            available = false;
            return;
        }

        Class<?> type = api.getClass();
        isFloodgatePlayer = findMethod(type, "isFloodgatePlayer", UUID.class);
        getPlayer = findMethod(type, "getPlayer", UUID.class);
        available = isFloodgatePlayer != null && getPlayer != null;
    }

    public static Optional<ReflectiveVelocityFloodgateProvider> discover() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        Optional<ReflectiveVelocityFloodgateProvider> discovered = discover(context);
        if (discovered.isPresent()) {
            return discovered;
        }
        ClassLoader own = ReflectiveVelocityFloodgateProvider.class.getClassLoader();
        return context == own ? Optional.empty() : discover(own);
    }

    public static Optional<ReflectiveVelocityFloodgateProvider> discover(ClassLoader classLoader) {
        ClassLoader loader = classLoader == null
                ? ReflectiveVelocityFloodgateProvider.class.getClassLoader()
                : classLoader;
        try {
            Class<?> apiType = Class.forName(FLOODGATE_API, false, loader);
            Method getInstance = apiType.getMethod("getInstance");
            Object api = unwrapOptional(getInstance.invoke(null));
            ReflectiveVelocityFloodgateProvider provider = new ReflectiveVelocityFloodgateProvider(api);
            return provider.available() ? Optional.of(provider) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return Optional.empty();
        }
    }

    public static ReflectiveVelocityFloodgateProvider unavailable() {
        return new ReflectiveVelocityFloodgateProvider(null);
    }

    @Override
    public Optional<FloodgatePlayer> find(UUID connectionUuid) {
        if (connectionUuid == null || !available() || failed) {
            return Optional.empty();
        }
        try {
            if (isFloodgatePlayer != null
                    && !Boolean.TRUE.equals(isFloodgatePlayer.invoke(api, connectionUuid))) {
                return Optional.empty();
            }

            Object value = getPlayer.invoke(api, connectionUuid);
            value = unwrapOptional(value);
            if (value == null) {
                if (isFloodgatePlayer != null) {
                    failed = true;
                }
                return Optional.empty();
            }

            String username = readText(
                    value,
                    "getJavaUsername",
                    "getCorrectUsername",
                    "getUsername",
                    "getName",
                    "username",
                    "name"
            );
            String xuid = readText(value, "getXuid", "xuid");
            if (username == null || xuid == null) {
                failed = true;
                return Optional.empty();
            }
            return Optional.of(new FloodgatePlayer(username, xuid));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            failed = true;
            return Optional.empty();
        }
    }

    @Override
    public boolean available() {
        return available && !failed;
    }

    @Override
    public boolean failed() {
        return failed;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }

    private static String readText(Object value, String... names) throws ReflectiveOperationException {
        for (String name : names) {
            Method method = findMethod(value.getClass(), name);
            if (method == null) {
                continue;
            }
            Object result;
            try {
                result = method.invoke(value);
            } catch (InvocationTargetException exception) {
                throw exception;
            }
            if (result == null) {
                return null;
            }
            String text = String.valueOf(result);
            return text.isBlank() ? null : text;
        }
        return null;
    }
}
