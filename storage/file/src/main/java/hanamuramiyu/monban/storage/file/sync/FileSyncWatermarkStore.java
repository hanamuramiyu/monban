package hanamuramiyu.monban.storage.file.sync;

import hanamuramiyu.monban.storage.file.AtomicFileSupport;
import hanamuramiyu.monban.storage.file.internal.FileYamlSupport;
import hanamuramiyu.monban.sync.SyncStoreException;
import hanamuramiyu.monban.sync.SyncWatermark;
import hanamuramiyu.monban.sync.SyncWatermarkStore;
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
 * File-backed watermark store ({@code sync-watermark.yml}, schema-version 1).
 * Strict parsing: unknown schema versions or corrupted content fail with
 * {@link SyncStoreException} (fail-closed).
 */
public final class FileSyncWatermarkStore implements SyncWatermarkStore {
    private static final int SCHEMA_VERSION = 1;
    private static final String LABEL = "Sync watermark";
    private static final String SCHEMA_VERSION_KEY = "schema-version";
    private static final String EPOCH_KEY = "epoch";
    private static final String REVISION_KEY = "revision";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_VERSION_KEY, EPOCH_KEY, REVISION_KEY);

    private final Path file;
    private final Yaml loader = FileYamlSupport.createLoader();
    private final Yaml dumper = FileYamlSupport.createDumper();

    public FileSyncWatermarkStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    @Override
    public Optional<SyncWatermark> load() {
        try {
            return loadDocument();
        } catch (IOException exception) {
            throw new SyncStoreException(
                    "Failed to load sync watermark from " + file + ": " + exception.getMessage(),
                    exception
            );
        }
    }

    @Override
    public void save(SyncWatermark watermark) {
        Objects.requireNonNull(watermark, "watermark");
        try {
            AtomicFileSupport.writeDurable(file, serialize(watermark));
        } catch (IOException exception) {
            throw new SyncStoreException(
                    "Failed to persist sync watermark to " + file + ": " + exception.getMessage(),
                    exception
            );
        }
    }

    private Optional<SyncWatermark> loadDocument() throws IOException {
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
            throw FileYamlSupport.invalidFile("Unsupported sync watermark schema version: " + schemaVersion);
        }

        UUID epoch = parseEpoch(FileYamlSupport.requireString(root.get(EPOCH_KEY), EPOCH_KEY));
        BigInteger revision = FileYamlSupport.requireInteger(root.get(REVISION_KEY), REVISION_KEY);
        if (revision.signum() < 1 || revision.bitLength() > 63) {
            throw FileYamlSupport.invalidFile("Invalid revision at " + REVISION_KEY + ": " + revision);
        }

        FileYamlSupport.deleteStaleTemporaryFile(temporaryFile, LABEL);
        return Optional.of(new SyncWatermark(epoch, revision.longValue()));
    }

    private String serialize(SyncWatermark watermark) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(SCHEMA_VERSION_KEY, SCHEMA_VERSION);
        root.put(EPOCH_KEY, watermark.epoch().toString());
        root.put(REVISION_KEY, watermark.revision());
        return dumper.dump(root);
    }

    private static UUID parseEpoch(String value) throws IOException {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw FileYamlSupport.invalidFile("Invalid epoch at " + EPOCH_KEY + ": " + value, exception);
        }
    }
}
