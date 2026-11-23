package hanamuramiyu.monban.config.file;

import hanamuramiyu.monban.sync.SyncSecret;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSyncSecretLoaderTest {
    @TempDir
    Path tempDirectory;

    @Test
    void missingFileDisablesSync() throws IOException {
        assertTrue(new FileSyncSecretLoader(tempDirectory.resolve("sync.yml")).load().isEmpty());
    }

    @Test
    void createsSecretWhenRequested() throws IOException {
        Path file = tempDirectory.resolve("sync.yml");

        SyncSecret secret = new FileSyncSecretLoader(file).loadOrCreate();

        assertTrue(Files.isRegularFile(file));
        assertEquals(secret, new FileSyncSecretLoader(file).load().orElseThrow());
        assertEquals(secret, new FileSyncSecretLoader(file).loadOrCreate());
    }

    @Test
    void loadsBase64Secret() throws IOException {
        String encoded = Base64.getEncoder().encodeToString(
                "monban-sync-secret".getBytes(StandardCharsets.UTF_8)
        );
        Path file = write("schema-version: 1\nsecret: " + encoded + "\n");

        assertTrue(new FileSyncSecretLoader(file).load().isPresent());
    }

    @Test
    void rejectsInvalidSecret() throws IOException {
        Path file = write("schema-version: 1\nsecret: short\n");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    @Test
    void rejectsUnknownFields() throws IOException {
        Path file = write("schema-version: 1\nsecret: bW9uYmFuLXN5bmMtc2VjcmV0\nextra: true\n");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    @Test
    void rejectsShortBase64Secret() throws IOException {
        Path file = write("schema-version: 1\nsecret: "
                + Base64.getEncoder().encodeToString(new byte[8]) + "\n");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    @Test
    void rejectsLongBase64Secret() throws IOException {
        Path file = write("schema-version: 1\nsecret: "
                + Base64.getEncoder().encodeToString(new byte[129]) + "\n");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    @Test
    void rejectsNonBase64Secret() throws IOException {
        Path file = write("schema-version: 1\nsecret: \"@@not-base64@@\"\n");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    @Test
    void rejectsDuplicateFields() throws IOException {
        String encoded = Base64.getEncoder().encodeToString(
                "monban-sync-secret".getBytes(StandardCharsets.UTF_8)
        );
        Path file = write("schema-version: 1\nsecret: " + encoded + "\nsecret: " + encoded + "\n");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    @Test
    void rejectsMissingSchemaVersion() throws IOException {
        Path file = write("secret: " + Base64.getEncoder().encodeToString(
                "monban-sync-secret".getBytes(StandardCharsets.UTF_8)
        ) + "\n");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    @Test
    void rejectsUnknownSchemaVersion() throws IOException {
        Path file = write("schema-version: 2\nsecret: " + Base64.getEncoder().encodeToString(
                "monban-sync-secret".getBytes(StandardCharsets.UTF_8)
        ) + "\n");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    @Test
    void rejectsSecretWithSurroundingWhitespace() throws IOException {
        String encoded = Base64.getEncoder().encodeToString(
                "monban-sync-secret".getBytes(StandardCharsets.UTF_8)
        );
        Path file = write("schema-version: 1\nsecret: \" " + encoded + " \"\n");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    @Test
    void rejectsEmptySecret() throws IOException {
        Path file = write("schema-version: 1\nsecret: \"\"\n");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    @Test
    void rejectsEmptyFile() throws IOException {
        Path file = write("");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    @Test
    void rejectsNonMappingRoot() throws IOException {
        Path file = write("- schema-version: 1\n");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    @Test
    void loadOrCreateDoesNotReplaceCorruptedSecretFile() throws IOException {
        Path file = write("schema-version: 1\nsecret: not-valid\n");
        String content = Files.readString(file);

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).loadOrCreate());

        assertEquals(content, Files.readString(file));
    }

    @Test
    void createdSecretFileIsOwnerOnlyOnPosix() throws IOException {
        Assumptions.assumeTrue(
                Files.getFileStore(tempDirectory).supportsFileAttributeView(PosixFileAttributeView.class),
                "POSIX file permissions are not supported"
        );
        Path file = tempDirectory.resolve("sync.yml");

        new FileSyncSecretLoader(file).loadOrCreate();

        assertEquals(
                PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(file)
        );
    }

    private Path write(String content) throws IOException {
        Path file = tempDirectory.resolve("sync.yml");
        Files.writeString(file, content);
        return file;
    }
}
