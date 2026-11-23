package hanamuramiyu.monban.storage.file.sync;

import hanamuramiyu.monban.storage.file.UnwritableTemporaryFileSupport;
import hanamuramiyu.monban.sync.SyncStoreException;
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

class FileSyncEpochStoreTest {
    private static final UUID EPOCH = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @TempDir
    Path temporaryDirectory;

    private Path file() {
        return temporaryDirectory.resolve("state-epoch");
    }

    private Path temporaryFile() {
        return temporaryDirectory.resolve("state-epoch.tmp");
    }

    @Test
    void savesAndLoadsEpoch() throws IOException {
        FileSyncEpochStore store = new FileSyncEpochStore(file());

        store.save(EPOCH);

        assertEquals(Optional.of(EPOCH), store.load());
        String content = Files.readString(file(), StandardCharsets.UTF_8);
        assertTrue(content.contains("schema-version: 1"));
        assertTrue(content.contains(EPOCH.toString()));
    }

    @Test
    void missingFileIsEmptyAndRepeatedSaveOverwrites() {
        FileSyncEpochStore store = new FileSyncEpochStore(file());

        assertEquals(Optional.empty(), store.load());

        store.save(EPOCH);
        store.save(UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"));

        assertEquals(Optional.of(UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")), store.load());
    }

    @Test
    void corruptedContentFailsClosed() throws IOException {
        Files.writeString(file(), "this is not valid yaml: [", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncEpochStore(file()).load());
    }

    @Test
    void nonUuidEpochFailsClosed() throws IOException {
        Files.writeString(file(), "schema-version: 1\nepoch: not-a-uuid\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncEpochStore(file()).load());
    }

    @Test
    void unknownSchemaVersionFailsClosed() throws IOException {
        Files.writeString(file(), "schema-version: 2\nepoch: " + EPOCH + "\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncEpochStore(file()).load());
    }

    @Test
    void missingSchemaVersionFailsClosed() throws IOException {
        Files.writeString(file(), "epoch: " + EPOCH + "\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncEpochStore(file()).load());
    }

    @Test
    void unknownFieldFailsClosed() throws IOException {
        Files.writeString(file(), "schema-version: 1\nepoch: " + EPOCH + "\nextra: 1\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncEpochStore(file()).load());
    }

    @Test
    void duplicateFieldsFailClosed() throws IOException {
        Files.writeString(
                file(),
                "schema-version: 1\nepoch: " + EPOCH + "\nepoch: " + EPOCH + "\n",
                StandardCharsets.UTF_8
        );

        assertThrows(SyncStoreException.class, () -> new FileSyncEpochStore(file()).load());
    }

    @Test
    void staleTemporaryFileIsIgnoredAndRemoved() throws IOException {
        FileSyncEpochStore store = new FileSyncEpochStore(file());
        store.save(EPOCH);
        Files.writeString(temporaryFile(), "stale", StandardCharsets.UTF_8);

        assertEquals(Optional.of(EPOCH), store.load());
        assertFalse(Files.exists(temporaryFile()));
    }

    @Test
    void saveLeavesNoTemporaryFile() {
        FileSyncEpochStore store = new FileSyncEpochStore(file());

        store.save(EPOCH);

        assertFalse(Files.exists(temporaryFile()));
        assertTrue(Files.exists(file()));
    }

    @Test
    void failingSaveThrowsAndLeavesPreviousFileIntact() throws IOException {
        FileSyncEpochStore store = new FileSyncEpochStore(file());
        store.save(EPOCH);
        String previousContent = Files.readString(file(), StandardCharsets.UTF_8);
        UnwritableTemporaryFileSupport.arrangeWriteFailure(temporaryFile());
        assertTrue(Files.exists(temporaryFile()));

        assertThrows(SyncStoreException.class, () -> store.save(UUID.randomUUID()));

        assertEquals(Optional.of(EPOCH), store.load());
        assertEquals(previousContent, Files.readString(file(), StandardCharsets.UTF_8));
        assertFalse(Files.exists(temporaryFile()));
    }

    @Test
    void missingEpochFailsClosed() throws IOException {
        Files.writeString(file(), "schema-version: 1\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncEpochStore(file()).load());
    }

    @Test
    void nonStringEpochFailsClosed() throws IOException {
        Files.writeString(file(), "schema-version: 1\nepoch: 123\n", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncEpochStore(file()).load());
    }

    @Test
    void emptyFileFailsClosed() throws IOException {
        Files.writeString(file(), "", StandardCharsets.UTF_8);

        assertThrows(SyncStoreException.class, () -> new FileSyncEpochStore(file()).load());
    }

    @Test
    void staleTemporaryFileIsRemovedWhenEpochIsMissing() throws IOException {
        Files.writeString(temporaryFile(), "stale", StandardCharsets.UTF_8);

        assertEquals(Optional.empty(), new FileSyncEpochStore(file()).load());
        assertFalse(Files.exists(temporaryFile()));
    }
}
