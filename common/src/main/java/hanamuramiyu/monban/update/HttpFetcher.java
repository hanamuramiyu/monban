package hanamuramiyu.monban.update;

import java.net.URI;
import java.util.Optional;

@FunctionalInterface
public interface HttpFetcher {
    Optional<String> fetch(URI uri);
}
