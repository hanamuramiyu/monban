package hanamuramiyu.monban.update;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public record UpdateSource(
        String name,
        URI uri,
        Function<String, Optional<MonbanVersion>> parser
) {
    public UpdateSource {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(parser, "parser");
    }
}
