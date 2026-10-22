package hanamuramiyu.monban.config;

public record FloodgateSettings(boolean enabled, boolean requireProvider) {
    public static FloodgateSettings defaults() { return new FloodgateSettings(false, false); }
}
