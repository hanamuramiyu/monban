package hanamuramiyu.monban.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class JdkHttpFetcher implements HttpFetcher {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client;
    private final String userAgent;

    public JdkHttpFetcher(String monbanVersion) {
        this(monbanVersion, HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build());
    }

    JdkHttpFetcher(String monbanVersion, HttpClient client) {
        this.userAgent = "monban/" + Objects.requireNonNull(monbanVersion, "monbanVersion");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Optional<String> fetch(URI uri) {
        Objects.requireNonNull(uri, "uri");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return Optional.of(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }
}
