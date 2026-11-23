package hanamuramiyu.monban.storage.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Durable atomic file writer: temporary file in the same directory,
 * {@link FileChannel#force(boolean)}, atomic replace and a best-effort parent
 * directory fsync. A directory fsync that the filesystem does not support is
 * skipped with a debug note; it never fails the write.
 */
public final class AtomicFileSupport {
    private static final System.Logger LOGGER = System.getLogger(AtomicFileSupport.class.getName());

    private AtomicFileSupport() {
    }

    public static void writeDurable(Path file, String contents) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);

        Path temporaryFile = file.resolveSibling(file.getFileName() + ".tmp");
        byte[] serialized = contents.getBytes(StandardCharsets.UTF_8);
        try {
            try (FileChannel channel = FileChannel.open(
                    temporaryFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(serialized);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            moveReplacing(temporaryFile, file);
            forceDirectory(parent);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(
                    System.Logger.Level.DEBUG,
                    "Directory fsync is not available for " + directory + ": " + exception
            );
        }
    }
}
