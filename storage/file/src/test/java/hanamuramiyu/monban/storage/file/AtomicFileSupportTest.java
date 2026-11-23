package hanamuramiyu.monban.storage.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFileSupportTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesAndReplacesContents() throws IOException {
        Path file = temporaryDirectory.resolve("state.txt");

        AtomicFileSupport.writeDurable(file, "first");
        AtomicFileSupport.writeDurable(file, "second");

        assertEquals("second", Files.readString(file, StandardCharsets.UTF_8));
        assertFalse(Files.exists(temporaryDirectory.resolve("state.txt.tmp")));
    }

    @Test
    void createsMissingParentDirectories() throws IOException {
        Path file = temporaryDirectory.resolve("nested/deeper/state.txt");

        AtomicFileSupport.writeDurable(file, "content");

        assertEquals("content", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void blockedParentPathFailsWithoutArtifacts() throws IOException {
        Path blocker = temporaryDirectory.resolve("blocker");
        Files.createFile(blocker);
        Path file = blocker.resolve("state.txt");

        assertThrows(IOException.class, () -> AtomicFileSupport.writeDurable(file, "content"));

        assertFalse(Files.exists(blocker.resolve("state.txt.tmp")));
    }

    @Test
    void failingWriteThrowsAndLeavesNoTemporaryFile() throws IOException {
        Path file = temporaryDirectory.resolve("state.txt");
        AtomicFileSupport.writeDurable(file, "previous");
        UnwritableTemporaryFileSupport.arrangeWriteFailure(temporaryDirectory.resolve("state.txt.tmp"));
        assertTrue(Files.exists(temporaryDirectory.resolve("state.txt.tmp")));

        assertThrows(IOException.class, () -> AtomicFileSupport.writeDurable(file, "next"));

        assertEquals("previous", Files.readString(file, StandardCharsets.UTF_8));
        assertFalse(Files.exists(temporaryDirectory.resolve("state.txt.tmp")));
    }

    @Test
    void directoryTargetFailsWithoutTouchingIt() throws IOException {
        Path file = temporaryDirectory.resolve("state.txt");
        Files.createDirectory(file);
        Files.writeString(file.resolve("kept.txt"), "kept", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> AtomicFileSupport.writeDurable(file, "content"));

        assertTrue(Files.exists(file.resolve("kept.txt")));
        assertFalse(Files.exists(temporaryDirectory.resolve("state.txt.tmp")));
    }
}
