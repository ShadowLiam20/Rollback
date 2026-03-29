package com.shadow.rollback.database;

import com.shadow.rollback.model.ActionRecord;
import com.shadow.rollback.model.ActionType;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

public class RollbackDatabase {

    private final JavaPlugin plugin;
    private Connection connection;

    public RollbackDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IllegalStateException("Could not create plugin data folder");
            }

            File databaseFolder = new File(plugin.getDataFolder(), "database");
            if (!databaseFolder.exists() && !databaseFolder.mkdirs()) {
                throw new IllegalStateException("Could not create database folder");
            }

            File databaseFile = new File(databaseFolder, "rollback.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS action_log (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            timestamp INTEGER NOT NULL,
                            actor_name TEXT,
                            world_id TEXT NOT NULL,
                            world_name TEXT NOT NULL,
                            x REAL NOT NULL,
                            y REAL NOT NULL,
                            z REAL NOT NULL,
                            action_type TEXT NOT NULL,
                            target_name TEXT,
                            before_data TEXT,
                            after_data TEXT,
                            rolled_back INTEGER NOT NULL DEFAULT 0
                        )
                        """);
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_action_time ON action_log(timestamp)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_action_world ON action_log(world_name)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_action_actor ON action_log(actor_name)");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to open rollback database", exception);
        }
    }

    public void close() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to close rollback database: " + exception.getMessage());
        } finally {
            connection = null;
        }
    }

    public void insert(ActionRecord record) {
        String sql = """
                INSERT INTO action_log (
                    timestamp, actor_name, world_id, world_name, x, y, z, action_type, target_name, before_data, after_data, rolled_back
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, record.timestamp());
            statement.setString(2, record.actorName());
            statement.setString(3, record.worldId().toString());
            statement.setString(4, record.worldName());
            statement.setDouble(5, record.x());
            statement.setDouble(6, record.y());
            statement.setDouble(7, record.z());
            statement.setString(8, record.actionType().name());
            statement.setString(9, record.targetName());
            statement.setString(10, record.beforeData());
            statement.setString(11, record.afterData());
            statement.setBoolean(12, record.rolledBack());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to insert action log", exception);
        }
    }

    public List<ActionRecord> findSince(long sinceEpochMillis, Integer limit) {
        String sql = """
                SELECT id, timestamp, actor_name, world_id, world_name, x, y, z, action_type, target_name, before_data, after_data, rolled_back
                FROM action_log
                WHERE timestamp >= ?
                ORDER BY id DESC
                LIMIT ?
                """;
        List<ActionRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sinceEpochMillis);
            statement.setInt(2, limit == null ? 5000 : Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(map(resultSet));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query rollback actions", exception);
        }
        return records;
    }

    public void setRolledBack(List<Long> ids, boolean rolledBack) {
        if (ids.isEmpty()) {
            return;
        }

        String sql = "UPDATE action_log SET rolled_back = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Long id : ids) {
                statement.setBoolean(1, rolledBack);
                statement.setLong(2, id);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update rollback state", exception);
        }
    }

    public int purgeSince(long sinceEpochMillis) {
        String sql = "DELETE FROM action_log WHERE timestamp >= ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sinceEpochMillis);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to purge rollback actions", exception);
        }
    }

    public int purgeAll() {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate("DELETE FROM action_log");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to purge rollback actions", exception);
        }
    }

    private ActionRecord map(ResultSet resultSet) throws SQLException {
        return new ActionRecord(
                resultSet.getLong("id"),
                resultSet.getLong("timestamp"),
                resultSet.getString("actor_name"),
                UUID.fromString(resultSet.getString("world_id")),
                resultSet.getString("world_name"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z"),
                ActionType.valueOf(resultSet.getString("action_type")),
                resultSet.getString("target_name"),
                resultSet.getString("before_data"),
                resultSet.getString("after_data"),
                resultSet.getBoolean("rolled_back")
        );
    }
}
