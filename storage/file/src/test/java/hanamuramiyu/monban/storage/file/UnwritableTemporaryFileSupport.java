package hanamuramiyu.monban.storage.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Arranges a realistic write failure at the temporary-file path so tests can prove that a failed
 * save leaves no {@code .tmp} artifact behind and does not touch the previously written file.
 *
 * <p>The trigger is the most realistic one the environment can express:
 * <ol>
 *   <li>a read-only {@code .tmp} file when file permissions are actually enforced (the usual CI and
 *       developer case) — the write is refused like a permission error;</li>
 *   <li>otherwise a {@code .tmp} symlink to {@code /dev/full} — the write fails with
 *       {@code ENOSPC} exactly like a full disk, which is the most common real cause of a failed
 *       save and does not depend on file permissions;</li>
 *   <li>otherwise an empty directory at the {@code .tmp} path — the write is refused as an
 *       unusable path.</li>
 * </ol>
 *
 * <p>Every trigger fails the write attempt, leaves an artifact at the temporary path for the code
 * under test to clean up, and never touches the target file. The assertions of the tests therefore
 * do not depend on which trigger is used.
 */
public final class UnwritableTemporaryFileSupport {
    private static final Path FULL_DEVICE = Path.of("/dev/full");
    private static final byte[] STALE_CONTENT = "stale".getBytes(StandardCharsets.UTF_8);

    private UnwritableTemporaryFileSupport() {
    }

    public static void arrangeWriteFailure(Path temporaryFile) throws IOException {
        if (tryReadOnlyFile(temporaryFile)) {
            return;
        }
        if (Files.exists(FULL_DEVICE)) {
            Files.createSymbolicLink(temporaryFile, FULL_DEVICE);
            return;
        }
        Files.createDirectory(temporaryFile);
    }

    private static boolean tryReadOnlyFile(Path temporaryFile) throws IOException {
        if (!permissionsAreEnforced(temporaryFile.getParent())) {
            return false;
        }
        Files.write(temporaryFile, STALE_CONTENT);
        Files.setPosixFilePermissions(temporaryFile, PosixFilePermissions.fromString("r--r--r--"));
        return true;
    }

    /**
     * File permissions are not a usable failure trigger when the tests run as a privileged user
     * (for example root in a container), so this is verified with a probe instead of assumed.
     */
    private static boolean permissionsAreEnforced(Path directory) {
        Path probe = directory.resolve("monban-write-probe");
        try {
            Files.write(probe, STALE_CONTENT);
            Files.setPosixFilePermissions(probe, PosixFilePermissions.fromString("r--r--r--"));
            try (FileChannel channel = FileChannel.open(probe, StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(STALE_CONTENT));
                return false;
            } catch (AccessDeniedException denied) {
                return true;
            } catch (IOException other) {
                return true;
            }
        } catch (IOException | UnsupportedOperationException environment) {
            return false;
        } finally {
            try {
                Files.deleteIfExists(probe);
            } catch (IOException ignored) {
                // probe cleanup must never fail the test
            }
        }
    }
}
