package com.getjobs;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class StarterDatabase implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        String databaseFile = environment.getProperty("internbridge.database-file");
        if (databaseFile == null || databaseFile.isBlank()
                || !("jdbc:sqlite:" + databaseFile).equals(environment.getProperty("spring.datasource.url"))) {
            return;
        }

        try {
            install(Path.of(databaseFile).toAbsolutePath().normalize());
        } catch (IOException error) {
            throw new IllegalStateException("无法初始化本地数据库 " + databaseFile + "。请检查运行目录是否可写。", error);
        }
    }

    private static void install(Path database) throws IOException {
        if (Files.isRegularFile(database)) {
            return;
        }

        Path directory = Files.createDirectories(database.getParent());
        try (InputStream starter = StarterDatabase.class.getResourceAsStream("/defaults/starter.sqlite")) {
            if (starter == null) {
                throw new IOException("Bundled starter database is missing");
            }
            Path temporary = Files.createTempFile(directory, "internbridge-", ".tmp");
            try {
                Files.copy(starter, temporary, StandardCopyOption.REPLACE_EXISTING);
                try {
                    // Publish the completed file without replacing a concurrent installation.
                    Files.createLink(database, temporary);
                } catch (FileAlreadyExistsException ignored) {
                    // The database already exists.
                } catch (UnsupportedOperationException | IOException linkError) {
                    try {
                        Files.move(temporary, database);
                    } catch (FileAlreadyExistsException ignored) {
                        // The database already exists.
                    }
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
