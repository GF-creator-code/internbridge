package com.getjobs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class StarterDatabaseTest {
    @Test
    void freshInstallGetsOnlyPublicOptionsAndExistingDataSurvivesRestart(@TempDir Path root) throws Exception {
        Path database = root.resolve("db/getjobs.db");
        StarterDatabase.install(database);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var sql = connection.createStatement()) {
            assertTrue(sql.executeQuery("select count(*) from boss_option").getInt(1) > 0);
            for (String table : new String[]{"cookie", "boss_data", "ai", "config", "boss_config"})
                assertEquals(0, sql.executeQuery("select count(*) from " + table).getInt(1));
            sql.executeUpdate("insert into boss_blacklist(type,value) values('company','My selection')");
        }
        StarterDatabase.install(database);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var sql = connection.createStatement()) {
            assertEquals("My selection", sql.executeQuery("select value from boss_blacklist").getString(1));
        }
    }
}
