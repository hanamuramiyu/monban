package hanamuramiyu.monban.config;

public record MetricsSettings(boolean enabled) {
    public static MetricsSettings defaults() {
        return new MetricsSettings(true);
    }
}
