package com.bcp.workshop.auth;

import io.quarkus.runtime.LaunchMode;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class UserRepository {
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
    private static final String CREATE_USERS_TABLE = "CREATE TABLE legacy_users ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "username TEXT UNIQUE, "
            + "password_hash TEXT NOT NULL, "
            + "display_name TEXT NOT NULL, "
            + "role TEXT NOT NULL)";
    private static final Set<String> USER_TABLE_COLUMNS = Set.of("id", "username", "password_hash", "display_name", "role");
    private static final String LEGACY_USERS_TABLE = "legacy_users_legacy";

    private final String databaseUrl;
    private final PasswordHasher passwordHasher;
    private final boolean seedDemoUsers;

    public UserRepository() {
        this(DB_URL, new PasswordHasher(), LaunchMode.current());
    }

    UserRepository(String databaseUrl, PasswordHasher passwordHasher) {
        this(databaseUrl, passwordHasher, LaunchMode.NORMAL);
    }

    UserRepository(String databaseUrl, PasswordHasher passwordHasher, LaunchMode launchMode) {
        this.databaseUrl = databaseUrl;
        this.passwordHasher = passwordHasher;
        this.seedDemoUsers = launchMode != LaunchMode.NORMAL;
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(databaseUrl)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA secure_delete=ON");
                }
                connection.setAutoCommit(false);
                try {
                    initializeUserTable(connection);
                    if (seedDemoUsers) {
                        seedDemoUsers(connection);
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException error) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackError) {
                        error.addSuppressed(rollbackError);
                    }
                    throw error;
                }
            }
        } catch (IOException | SQLException error) {
            throw new IllegalStateException("Unable to initialize authentication storage", error);
        }
    }

    private void seedDemoUsers(Connection connection) throws SQLException {
        insertSeedUserIfMissing(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
        insertSeedUserIfMissing(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
    }

    public UserRecord findByUsername(String username) {
        String sql = "SELECT id, username, password_hash, display_name, role FROM legacy_users WHERE username=?";
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new UserRecord(result.getLong("id"), result.getString("username"),
                        result.getString("password_hash"),
                        result.getString("display_name"), result.getString("role"));
            }
        } catch (SQLException error) {
            throw new AuthenticationServiceException("Unable to access authentication storage", error);
        }
    }

    private void initializeUserTable(Connection connection) throws SQLException {
        if (!tableExists(connection, "legacy_users")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_USERS_TABLE);
            }
            return;
        }

        Set<String> existingColumns = columnsIn(connection, "legacy_users");
        if (existingColumns.contains("password") || !existingColumns.contains("password_hash")) {
            migrateLegacyUsers(connection, existingColumns.contains("password_hash"));
        } else if (!existingColumns.containsAll(USER_TABLE_COLUMNS)) {
            throw new SQLException("Unexpected legacy_users schema");
        }
    }

    private void migrateLegacyUsers(Connection connection, boolean containsPasswordHash) throws SQLException {
        String selectColumns = containsPasswordHash
                ? "id, username, password, password_hash, display_name, role"
                : "id, username, password, display_name, role";
        List<LegacyUser> legacyUsers = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT " + selectColumns + " FROM legacy_users")) {
            while (result.next()) {
                String passwordHash = containsPasswordHash ? result.getString("password_hash") : null;
                if (passwordHash == null || passwordHash.isBlank()) {
                    passwordHash = passwordHasher.hash(result.getString("password"));
                }
                legacyUsers.add(new LegacyUser(
                        result.getLong("id"),
                        result.getString("username"),
                        passwordHash,
                        result.getString("display_name"),
                        result.getString("role")));
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE legacy_users RENAME TO " + LEGACY_USERS_TABLE);
            statement.executeUpdate(CREATE_USERS_TABLE);
        }
        copyUsers(connection, legacyUsers);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE " + LEGACY_USERS_TABLE);
        }
    }

    private void copyUsers(Connection connection, List<LegacyUser> users) throws SQLException {
        String sql = "INSERT INTO legacy_users(id, username, password_hash, display_name, role) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (LegacyUser user : users) {
                statement.setLong(1, user.id());
                statement.setString(2, user.username());
                statement.setString(3, user.passwordHash());
                statement.setString(4, user.displayName());
                statement.setString(5, user.role());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertSeedUserIfMissing(
            Connection connection, String username, String password, String displayName, String role) throws SQLException {
        if (userExists(connection, username)) {
            return;
        }
        String sql = "INSERT INTO legacy_users(username, password_hash, display_name, role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHasher.hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private boolean userExists(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private Set<String> columnsIn(Connection connection, String tableName) throws SQLException {
        Set<String> columns = new java.util.HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (result.next()) {
                columns.add(result.getString("name"));
            }
        }
        return columns;
    }

    private record LegacyUser(long id, String username, String passwordHash, String displayName, String role) {}
}
