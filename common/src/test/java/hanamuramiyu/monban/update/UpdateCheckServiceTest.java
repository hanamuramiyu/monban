package hanamuramiyu.monban.update;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckServiceTest {
    private static final String CURRENT_VERSION = "3.3.0";

    @Test
    void picksHighestVersionFromModrinthResponse() {
        UpdateCheckService service = new UpdateCheckService(
                fetcher(Optional.of("""
                        [
                          {"version_number":"3.3.0"},
                          {"version_number":"3.4.1"},
                          {"version_number":"3.4.0"}
                        ]
                        """), Optional.empty()),
                CURRENT_VERSION
        );

        UpdateCheckResult result = service.check().orElseThrow();

        assertEquals("3.4.1", result.latest().toString());
        assertEquals("3.3.0", result.current().toString());
    }

    @Test
    void ignoresUnparseableModrinthEntries() {
        UpdateCheckService service = new UpdateCheckService(
                fetcher(Optional.of("""
                        [
                          {"version_number":"nightly"},
                          {"version_number":"v3.4.0-beta.1"},
                          {"version_number":"unknown"}
                        ]
                        """), Optional.empty()),
                CURRENT_VERSION
        );

        UpdateCheckResult result = service.check().orElseThrow();

        assertEquals("3.4.0", result.latest().toString());
    }

    @Test
    void fallsBackToGithubWhenModrinthIsUnavailable() {
        UpdateCheckService service = new UpdateCheckService(
                fetcher(Optional.empty(), Optional.of("{\"tag_name\":\"v3.4.0\"}")),
                CURRENT_VERSION
        );

        UpdateCheckResult result = service.check().orElseThrow();

        assertEquals("3.4.0", result.latest().toString());
    }

    @Test
    void fallsBackToGithubWhenModrinthHasNoNewerVersion() {
        UpdateCheckService service = new UpdateCheckService(
                fetcher(Optional.of("[{\"version_number\":\"3.3.0\"}]"), Optional.of("{\"tag_name\":\"v3.4.0\"}")),
                CURRENT_VERSION
        );

        UpdateCheckResult result = service.check().orElseThrow();

        assertEquals("3.4.0", result.latest().toString());
    }

    @Test
    void fallsBackToGithubWhenModrinthResponseIsInvalid() {
        UpdateCheckService service = new UpdateCheckService(
                fetcher(Optional.of("<html>not json</html>"), Optional.of("{\"tag_name\":\"v3.4.0\"}")),
                CURRENT_VERSION
        );

        UpdateCheckResult result = service.check().orElseThrow();

        assertEquals("3.4.0", result.latest().toString());
    }

    @Test
    void returnsEmptyWhenBothSourcesAreUnavailable() {
        UpdateCheckService service = new UpdateCheckService(
                fetcher(Optional.empty(), Optional.empty()),
                CURRENT_VERSION
        );

        assertTrue(service.check().isEmpty());
    }

    @Test
    void returnsEmptyWhenGithubResponseIsInvalid() {
        UpdateCheckService service = new UpdateCheckService(
                fetcher(Optional.empty(), Optional.of("{}")),
                CURRENT_VERSION
        );

        assertTrue(service.check().isEmpty());
    }

    @Test
    void returnsEmptyWhenLatestVersionIsNotNewer() {
        UpdateCheckService service = new UpdateCheckService(
                fetcher(Optional.of("[{\"version_number\":\"3.3.0\"}]"), Optional.empty()),
                CURRENT_VERSION
        );

        assertTrue(service.check().isEmpty());
    }

    @Test
    void returnsEmptyWhenLatestVersionIsOlder() {
        UpdateCheckService service = new UpdateCheckService(
                fetcher(Optional.of("[{\"version_number\":\"3.2.9\"}]"), Optional.empty()),
                CURRENT_VERSION
        );

        assertTrue(service.check().isEmpty());
    }

    @Test
    void returnsEmptyWhenCurrentVersionCannotBeParsed() {
        AtomicInteger calls = new AtomicInteger();
        HttpFetcher countingFetcher = uri -> {
            calls.incrementAndGet();
            return Optional.of("[{\"version_number\":\"3.4.0\"}]");
        };

        UpdateCheckService service = new UpdateCheckService(countingFetcher, "development");

        assertTrue(service.check().isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void continuesToNextSourceWhenFetcherThrows() {
        HttpFetcher fetcher = uri -> {
            if (uri.equals(UpdateCheckService.MODRINTH_PROJECT_VERSIONS)) {
                throw new IllegalStateException("network is down");
            }
            return Optional.of("{\"tag_name\":\"3.4.0\"}");
        };

        UpdateCheckService service = new UpdateCheckService(fetcher, CURRENT_VERSION);

        assertEquals("3.4.0", service.check().orElseThrow().latest().toString());
    }

    @Test
    void doesNotQueryGithubWhenModrinthAlreadyHasNewerVersion() {
        AtomicInteger githubCalls = new AtomicInteger();
        HttpFetcher fetcher = uri -> {
            if (uri.equals(UpdateCheckService.MODRINTH_PROJECT_VERSIONS)) {
                return Optional.of("[{\"version_number\":\"3.4.0\"}]");
            }
            githubCalls.incrementAndGet();
            return Optional.empty();
        };

        UpdateCheckService service = new UpdateCheckService(fetcher, CURRENT_VERSION);

        assertEquals("3.4.0", service.check().orElseThrow().latest().toString());
        assertEquals(0, githubCalls.get());
    }

    @Test
    void messageContainsVersionsAndDownloadUrl() {
        UpdateCheckService service = new UpdateCheckService(
                fetcher(Optional.of("[{\"version_number\":\"3.4.0\"}]"), Optional.empty()),
                CURRENT_VERSION
        );

        String message = service.check().orElseThrow().message();

        assertEquals(
                "A newer monban version is available: 3.4.0 (current 3.3.0). "
                        + "Download: https://modrinth.com/plugin/monban",
                message
        );
    }

    @Test
    void usesConfiguredSourceOrder() {
        URI first = URI.create("https://example.invalid/first");
        URI second = URI.create("https://example.invalid/second");
        UpdateSource firstSource = new UpdateSource(
                "First",
                first,
                body -> MonbanVersion.parse(body.trim())
        );
        UpdateSource secondSource = new UpdateSource(
                "Second",
                second,
                body -> MonbanVersion.parse(body.trim())
        );
        HttpFetcher fetcher = uri -> uri.equals(first)
                ? Optional.of("3.4.0")
                : Optional.of("3.5.0");

        UpdateCheckService service = new UpdateCheckService(
                fetcher,
                CURRENT_VERSION,
                java.util.List.of(firstSource, secondSource)
        );

        assertEquals("3.4.0", service.check().orElseThrow().latest().toString());
    }

    private static HttpFetcher fetcher(Optional<String> modrinth, Optional<String> github) {
        return uri -> {
            if (uri.equals(UpdateCheckService.MODRINTH_PROJECT_VERSIONS)) {
                return modrinth;
            }
            if (uri.equals(UpdateCheckService.GITHUB_LATEST_RELEASE)) {
                return github;
            }
            return Optional.empty();
        };
    }
}
