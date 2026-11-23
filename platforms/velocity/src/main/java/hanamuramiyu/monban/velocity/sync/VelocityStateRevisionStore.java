package hanamuramiyu.monban.velocity.sync;

import hanamuramiyu.monban.storage.file.AtomicFileSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class VelocityStateRevisionStore implements StateRevisionStore {
    private final Path file;

    VelocityStateRevisionStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    @Override
    public long load() {
        if (!Files.exists(file)) {
            return 0;
        }
        try {
            if (!Files.isRegularFile(file)) {
                throw new IOException("State revision path is not a regular file: " + file);
            }
            long revision = Long.parseLong(Files.readString(file, StandardCharsets.UTF_8).strip());
            if (revision < 0) {
                throw new IOException("State revision must not be negative: " + revision);
            }
            return revision;
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalStateException("Failed to load state revision from " + file, exception);
        }
    }

    @Override
    public void save(long revision) {
        if (revision < 1) {
            throw new IllegalArgumentException("State revision must be positive.");
        }
        try {
            AtomicFileSupport.writeDurable(file, Long.toString(revision));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist state revision to " + file, exception);
        }
    }
}
