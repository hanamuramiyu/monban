package hanamuramiyu.monban.update;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MonbanVersion(int major, int minor, int patch) implements Comparable<MonbanVersion> {
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$",
            Pattern.CASE_INSENSITIVE
    );

    public MonbanVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version components must not be negative.");
        }
    }

    public static Optional<MonbanVersion> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        Matcher matcher = VERSION_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new MonbanVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            ));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    @Override
    public int compareTo(MonbanVersion other) {
        int result = Integer.compare(major, other.major);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(minor, other.minor);
        if (result != 0) {
            return result;
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
