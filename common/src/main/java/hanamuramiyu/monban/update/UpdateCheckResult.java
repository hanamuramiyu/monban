package hanamuramiyu.monban.update;

import java.util.Objects;

public record UpdateCheckResult(MonbanVersion current, MonbanVersion latest) {
    public static final String DOWNLOAD_URL = "https://modrinth.com/plugin/monban";

    public UpdateCheckResult {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(latest, "latest");
    }

    public String message() {
        return "A newer monban version is available: " + latest
                + " (current " + current + "). Download: " + DOWNLOAD_URL;
    }
}
