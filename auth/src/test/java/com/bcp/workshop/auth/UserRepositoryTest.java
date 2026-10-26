package com.bcp.workshop.auth;

import io.quarkus.runtime.LaunchMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRepositoryTest {
    private static final String EXISTING_PASSWORD = "LegacyPass-10";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesPlainTextDatabaseAndPreservesExistingLogin() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("legacy-auth.sqlite");
        String databaseUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath().toString().replace('\\', '/');
        createLegacyDatabase(databaseUrl);

        PasswordHasher passwordHasher = new PasswordHasher();
        UserRepository database = new UserRepository(databaseUrl, passwordHasher);
        database.initialize();

        assertEquals(Set.of("id", "username", "password_hash", "display_name", "role"),
                columnsIn(databaseUrl));
        UserRecord user = database.findByUsername("legacy-user");
        assertNotNull(user);
        assertEquals("Legacy User", user.displayName());
        assertTrue(passwordHasher.verify(EXISTING_PASSWORD, user.passwordHash()));
        assertFalse(passwordHasher.verify("incorrect", user.passwordHash()));
        assertFalse(tableExists(databaseUrl, "legacy_users_legacy"));
    }

    @Test
    void migrationDoesNotLeaveLegacyPasswordBytesInDatabaseFile() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("legacy-password-pages.sqlite");
        String databaseUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath().toString().replace('\\', '/');
        createLegacyDatabase(databaseUrl);

        UserRepository database = new UserRepository(databaseUrl, new PasswordHasher());
        database.initialize();

        String databaseContents = new String(Files.readAllBytes(databaseFile), StandardCharsets.ISO_8859_1);
        assertFalse(databaseContents.contains(EXISTING_PASSWORD));
    }

    @Test
    void doesNotSeedDemoAccountsInNormalMode() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("unseeded-auth.sqlite");
        String databaseUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath().toString().replace('\\', '/');

        UserRepository database = new UserRepository(databaseUrl, new PasswordHasher(), LaunchMode.NORMAL);
        database.initialize();

        assertNull(database.findByUsername("student"));
        assertNull(database.findByUsername("trainer"));
    }

    @Test
    void seedsDemoAccountsInTestMode() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("seeded-auth.sqlite");
        String databaseUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath().toString().replace('\\', '/');
        PasswordHasher passwordHasher = new PasswordHasher();

        UserRepository database = new UserRepository(databaseUrl, passwordHasher, LaunchMode.TEST);
        database.initialize();

        UserRecord student = database.findByUsername("student");
        UserRecord trainer = database.findByUsername("trainer");
        assertNotNull(student);
        assertNotNull(trainer);
        assertTrue(passwordHasher.verify("Workshop123!", student.passwordHash()));
        assertTrue(passwordHasher.verify("Trainer123!", trainer.passwordHash()));
    }

    private static void createLegacyDatabase(String databaseUrl) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
        }

        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO legacy_users(username, password, display_name, role) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, "legacy-user");
            statement.setString(2, EXISTING_PASSWORD);
            statement.setString(3, "Legacy User");
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }
    }

    private static Set<String> columnsIn(String databaseUrl) throws Exception {
        Set<String> columns = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (result.next()) {
                columns.add(result.getString("name"));
            }
        }
        return columns;
    }

    private static boolean tableExists(String databaseUrl, String tableName) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
