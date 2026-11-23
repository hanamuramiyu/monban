package hanamuramiyu.monban.storage.file.sync;

import hanamuramiyu.monban.storage.file.UnwritableTemporaryFileSupport;
import hanamuramiyu.monban.sync.SyncStoreException;
import hanamuramiyu.monban.sync.SyncWatermark;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSyncWatermarkStoreTest {
    private static final UUID EPOCH = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final SyncWatermark WATERMARK = new SyncWatermark(EPOCH, 42);

    @TempDir
    Path temporaryDirectory;

    private Path file() {
        return temporaryDirectory.resolve("sync-watermark.yml");
    }

    private Path temporaryFile() {
        return temporaryDirectory.resolve("sync-watermark.yml.tmp");
    }

    @Test
    void savesAndLoadsWatermark() throws IOException {
        FileSyncWatermarkStore store = new FileSyncWatermarkStore(file());

        store.save(WATERMARK);

        assertEquals(Optional.of(WATERMARK), store.load());
        String content = Files.readString(file(), StandardCharsets.UTF_8);
        assertTrue(content.contains("schema-version: 1"));
        assertTrue(content.contains(EPOCH.toString()));
        assertTrue(content.contains("revision: 42"));
    }

    @Test
    void missingFileIsEmpty() {
        assertEquals(Optional.empty(), new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void corruptedContentFailsClosed() throws IOException {
        Files.writeString(file(), "this is not valid yaml: [", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void unknownSchemaVersionFailsClosed() throws IOException {
        Files.writeString(file(), "schema-version: 2\nepoch: " + EPOCH + "\nrevision: 42\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void garbageRootFailsClosed() throws IOException {
        Files.writeString(file(), "just some text\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void nonUuidEpochFailsClosed() throws IOException {
        Files.writeString(file(), "schema-version: 1\nepoch: not-a-uuid\nrevision: 42\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void nonPositiveRevisionFailsClosed() throws IOException {
        Files.writeString(file(), "schema-version: 1\nepoch: " + EPOCH + "\nrevision: 0\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void unknownFieldFailsClosed() throws IOException {
        Files.writeString(
                file(),
                "schema-version: 1\nepoch: " + EPOCH + "\nrevision: 42\nextra: 1\n",
                StandardCharsets.UTF_8
        );

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void duplicateFieldsFailClosed() throws IOException {
        Files.writeString(
                file(),
                "schema-version: 1\nepoch: " + EPOCH + "\nrevision: 42\nrevision: 43\n",
                StandardCharsets.UTF_8
        );

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void failingSaveThrowsLeavesNoTemporaryFileAndKeepsPreviousWatermark() throws IOException {
        FileSyncWatermarkStore store = new FileSyncWatermarkStore(file());
        store.save(WATERMARK);
        String previousContent = Files.readString(file(), StandardCharsets.UTF_8);
        UnwritableTemporaryFileSupport.arrangeWriteFailure(temporaryFile());
        assertTrue(Files.exists(temporaryFile()));

        assertThrows(SyncStoreException.class, () -> store.save(new SyncWatermark(EPOCH, 43)));

        assertEquals(Optional.of(WATERMARK), store.load());
        assertEquals(previousContent, Files.readString(file(), StandardCharsets.UTF_8));
        assertFalse(Files.exists(temporaryFile()));
    }

    @Test
    void failedMoveLeavesNoTemporaryFileAndFailsClosed() throws IOException {
        // The temporary file is really written before the atomic move fails, so this proves the
        // cleanup of a temp file produced by the save itself (the target path is occupied).
        FileSyncWatermarkStore store = new FileSyncWatermarkStore(file());
        Files.createDirectory(file());
        Files.writeString(file().resolve("kept.txt"), "kept", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> store.save(WATERMARK));

        assertFalse(Files.exists(temporaryFile()));
        assertTrue(Files.exists(file().resolve("kept.txt")));
        assertThrows(SyncStoreException.class, store::load);
    }

    @Test
    void staleTemporaryFileIsIgnoredAndRemoved() throws IOException {
        FileSyncWatermarkStore store = new FileSyncWatermarkStore(file());
        store.save(WATERMARK);
        Files.writeString(temporaryFile(), "stale", StandardCharsets.UTF_8);

        assertEquals(Optional.of(WATERMARK), store.load());
        assertFalse(Files.exists(temporaryFile()));
    }

    @Test
    void staleTemporaryFileIsRemovedWhenWatermarkIsMissing() throws IOException {
        Files.writeString(temporaryFile(), "stale", StandardCharsets.UTF_8);
        FileSyncWatermarkStore store = new FileSyncWatermarkStore(file());

        assertEquals(Optional.empty(), store.load());
        assertFalse(Files.exists(temporaryFile()));
    }

    @Test
    void saveLeavesNoTemporaryFile() {
        FileSyncWatermarkStore store = new FileSyncWatermarkStore(file());

        store.save(WATERMARK);

        assertFalse(Files.exists(temporaryFile()));
        assertTrue(Files.exists(file()));
    }

    @Test
    void negativeRevisionFailsClosed() throws IOException {
        Files.writeString(file(), "schema-version: 1\nepoch: " + EPOCH + "\nrevision: -1\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void revisionAboveLongRangeFailsClosed() throws IOException {
        Files.writeString(
                file(),
                "schema-version: 1\nepoch: " + EPOCH + "\nrevision: 9223372036854775808\n",
                StandardCharsets.UTF_8
        );

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void nonIntegerRevisionFailsClosed() throws IOException {
        Files.writeString(file(), "schema-version: 1\nepoch: " + EPOCH + "\nrevision: 1.5\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void missingSchemaVersionFailsClosed() throws IOException {
        Files.writeString(file(), "epoch: " + EPOCH + "\nrevision: 42\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void missingEpochFailsClosed() throws IOException {
        Files.writeString(file(), "schema-version: 1\nrevision: 42\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void missingRevisionFailsClosed() throws IOException {
        Files.writeString(file(), "schema-version: 1\nepoch: " + EPOCH + "\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void nonStringEpochFailsClosed() throws IOException {
        Files.writeString(file(), "schema-version: 1\nepoch: 123\nrevision: 42\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }

    @Test
    void emptyFileFailsClosed() throws IOException {
        Files.writeString(file(), "", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncWatermarkStore(file()).load());
    }
}
