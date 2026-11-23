package hanamuramiyu.monban.storage.file.sync;

import hanamuramiyu.monban.storage.file.AtomicFileSupport;
import hanamuramiyu.monban.storage.file.internal.FileYamlSupport;
import hanamuramiyu.monban.sync.SyncStoreException;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * File-backed epoch store ({@code state-epoch}, schema-version 1). Strict parsing:
 * unknown schema versions or corrupted content fail with {@link SyncStoreException}
 * (fail-closed); a missing file is not an error.
 */
public final class FileSyncEpochStore {
    private static final int SCHEMA_VERSION = 1;
    private static final String LABEL = "State epoch";
    private static final String SCHEMA_VERSION_KEY = "schema-version";
    private static final String EPOCH_KEY = "epoch";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_VERSION_KEY, EPOCH_KEY);

    private final Path file;
    private final Yaml loader = FileYamlSupport.createLoader();
    private final Yaml dumper = FileYamlSupport.createDumper();

    public FileSyncEpochStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public Optional<UUID> load() {
        try {
            return loadDocument();
        } catch (IOException exception) {
            throw new SyncStoreException(
                    "Failed to load state epoch from " + file + ": " + exception.getMessage(),
                    exception
            );
        }
    }

    public void save(UUID epoch) {
        Objects.requireNonNull(epoch, "epoch");
        try {
            AtomicFileSupport.writeDurable(file, serialize(epoch));
        } catch (IOException exception) {
            throw new SyncStoreException(
                    "Failed to persist state epoch to " + file + ": " + exception.getMessage(),
                    exception
            );
        }
    }

    private Optional<UUID> loadDocument() throws IOException {
        Path temporaryFile = FileYamlSupport.temporaryFile(file);
        if (!Files.exists(file)) {
            FileYamlSupport.deleteStaleTemporaryFile(temporaryFile, LABEL);
            return Optional.empty();
        }
        if (!Files.isRegularFile(file)) {
            throw FileYamlSupport.invalidFile(LABEL + " path is not a regular file: " + file);
        }

        Map<?, ?> root = FileYamlSupport.requireMap(FileYamlSupport.loadDocument(loader, file, LABEL), "root");
        FileYamlSupport.requireOnlyFields(root, "root", ROOT_FIELDS);

        BigInteger schemaVersion = FileYamlSupport.requireInteger(root.get(SCHEMA_VERSION_KEY), SCHEMA_VERSION_KEY);
        if (!BigInteger.valueOf(SCHEMA_VERSION).equals(schemaVersion)) {
            throw FileYamlSupport.invalidFile("Unsupported state epoch schema version: " + schemaVersion);
        }

        String epochValue = FileYamlSupport.requireString(root.get(EPOCH_KEY), EPOCH_KEY);
        UUID epoch;
        try {
            epoch = UUID.fromString(epochValue);
        } catch (IllegalArgumentException exception) {
            throw FileYamlSupport.invalidFile("Invalid epoch at " + EPOCH_KEY + ": " + epochValue, exception);
        }

        FileYamlSupport.deleteStaleTemporaryFile(temporaryFile, LABEL);
        return Optional.of(epoch);
    }

    private String serialize(UUID epoch) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(SCHEMA_VERSION_KEY, SCHEMA_VERSION);
        root.put(EPOCH_KEY, epoch.toString());
        return dumper.dump(root);
    }
}
