package hanamuramiyu.monban.update;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateCheckService {
    public static final URI MODRINTH_PROJECT_VERSIONS = URI.create(
            "https://api.modrinth.com/v2/project/monban/version"
    );
    public static final URI GITHUB_LATEST_RELEASE = URI.create(
            "https://api.github.com/repos/hanamuramiyu/monban/releases/latest"
    );

    private static final Pattern MODRINTH_VERSION_NUMBER = Pattern.compile(
            "\"version_number\"\\s*:\\s*\"([^\"]+)\""
    );
    private static final Pattern GITHUB_TAG_NAME = Pattern.compile(
            "\"tag_name\"\\s*:\\s*\"([^\"]+)\""
    );

    private final HttpFetcher fetcher;
    private final Optional<MonbanVersion> currentVersion;
    private final List<UpdateSource> sources;

    public UpdateCheckService(HttpFetcher fetcher, String currentVersion) {
        this(fetcher, currentVersion, List.of(
                new UpdateSource("Modrinth", MODRINTH_PROJECT_VERSIONS, UpdateCheckService::parseModrinthVersions),
                new UpdateSource("GitHub", GITHUB_LATEST_RELEASE, UpdateCheckService::parseGithubRelease)
        ));
    }

    public UpdateCheckService(HttpFetcher fetcher, String currentVersion, List<UpdateSource> sources) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.currentVersion = MonbanVersion.parse(currentVersion);
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
    }

    public Optional<UpdateCheckResult> check() {
        if (currentVersion.isEmpty()) {
            return Optional.empty();
        }
        MonbanVersion current = currentVersion.orElseThrow();
        for (UpdateSource source : sources) {
            Optional<MonbanVersion> latest;
            try {
                latest = fetcher.fetch(source.uri()).flatMap(source.parser());
            } catch (RuntimeException exception) {
                continue;
            }
            if (latest.isEmpty()) {
                continue;
            }
            MonbanVersion candidate = latest.orElseThrow();
            if (candidate.compareTo(current) > 0) {
                return Optional.of(new UpdateCheckResult(current, candidate));
            }
        }
        return Optional.empty();
    }

    static Optional<MonbanVersion> parseModrinthVersions(String body) {
        Matcher matcher = MODRINTH_VERSION_NUMBER.matcher(body);
        MonbanVersion highest = null;
        while (matcher.find()) {
            Optional<MonbanVersion> candidate = MonbanVersion.parse(matcher.group(1));
            if (candidate.isEmpty()) {
                continue;
            }
            MonbanVersion version = candidate.orElseThrow();
            if (highest == null || version.compareTo(highest) > 0) {
                highest = version;
            }
        }
        return Optional.ofNullable(highest);
    }

    static Optional<MonbanVersion> parseGithubRelease(String body) {
        Matcher matcher = GITHUB_TAG_NAME.matcher(body);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return MonbanVersion.parse(matcher.group(1));
    }
}
