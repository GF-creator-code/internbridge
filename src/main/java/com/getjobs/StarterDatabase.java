package com.getjobs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Publish a fully written starter once. Never overwrite an existing installation. */
final class StarterDatabase {
    static void install(Path database) throws IOException {
        if (Files.exists(database)) return;
        Path directory = Files.createDirectories(database.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(directory, "internbridge-", ".tmp");
        try (InputStream starter = StarterDatabase.class.getResourceAsStream("/defaults/starter.sqlite")) {
            if (starter == null) throw new IOException("Bundled starter database is missing");
            Files.copy(starter, temporary, StandardCopyOption.REPLACE_EXISTING);
            // Same-directory hard link is an atomic create-if-absent on NTFS, APFS and ext4.
            try {
                Files.createLink(database, temporary);
            } catch (FileAlreadyExistsException alreadyInstalled) {
                // Another instance completed installation first.
            } catch (UnsupportedOperationException | IOException unsupported) {
                try {
                    Files.move(temporary, database);
                } catch (FileAlreadyExistsException alreadyInstalled) {
                    // Existing installations always take precedence.
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private StarterDatabase() {}
}
