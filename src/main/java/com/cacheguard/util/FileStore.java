package com.cacheguard.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Reads and writes small UTF-8 text files in the application data folder.
 * Writes go to a temporary file first and are then moved into place, so a crash
 * mid-write can never leave a half-written settings or history file behind.
 */
public final class FileStore {

    private final Path directory;

    public FileStore(Path directory) {
        this.directory = directory;
    }

    /** {@code -Dcacheguard.home=...} overrides the default {@code ~/.cacheguard}. */
    public static Path defaultDirectory() {
        String override = System.getProperty("cacheguard.home");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home", "."), ".cacheguard");
    }

    public Path directory() {
        return directory;
    }

    public Path resolve(String name) {
        return directory.resolve(name);
    }

    public Optional<String> read(String name) throws IOException {
        Path file = resolve(name);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
    }

    public void write(String name, String content) throws IOException {
        writeAtomically(resolve(name), content);
    }

    public static void writeAtomically(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "cacheguard-", ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Renames an unreadable file out of the way so the app can start fresh without losing it. */
    public Optional<Path> quarantine(String name) {
        Path file = resolve(name);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path target = resolve(name + ".corrupt-" + stamp);
        try {
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(target);
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
