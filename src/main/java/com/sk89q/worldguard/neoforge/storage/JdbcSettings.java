package com.sk89q.worldguard.neoforge.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** JDBC credentials are never included in diagnostic text. */
public record JdbcSettings(String dsn, String username, String password, String prefix) {
    public JdbcSettings {
        if (dsn == null || !(dsn.startsWith("jdbc:sqlite:") || dsn.startsWith("jdbc:mysql:") || dsn.startsWith("jdbc:mariadb:")))
            throw new IllegalArgumentException("SQL requires a jdbc:sqlite:, jdbc:mysql: or jdbc:mariadb: URL");
        if (prefix == null || !prefix.matches("[A-Za-z0-9_]{0,40}")) throw new IllegalArgumentException("Invalid SQL table prefix");
        if(dsn.startsWith("jdbc:sqlite:") && (dsn.equals("jdbc:sqlite:") || dsn.contains(":memory:") || dsn.contains("mode=memory")))
            throw new IllegalArgumentException("WorldGuard SQL storage requires a persistent SQLite file");
        username = username == null ? "" : username;
        password = password == null ? "" : password;
    }
    public boolean sqlite() { return dsn.startsWith("jdbc:sqlite:"); }
    public String table(String suffix) {
        if (!suffix.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid SQL table name");
        return "`" + prefix + suffix + "`";
    }
    public Connection open() throws SQLException {
        try {
            Class.forName(sqlite() ? "org.sqlite.JDBC" : "org.mariadb.jdbc.Driver");
            if (sqlite()) {
                String filename = dsn.substring("jdbc:sqlite:".length());
                if (!filename.startsWith(":") && !filename.startsWith("file:")) {
                    Path parent = Path.of(filename).toAbsolutePath().getParent();
                    if (parent != null) Files.createDirectories(parent);
                }
            }
        } catch (Exception e) { throw new SQLException("Unable to initialize the SQL driver", e); }
        Properties properties = new Properties();
        properties.setProperty("user", username); properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "6000"); properties.setProperty("socketTimeout", "10000");
        properties.setProperty("permitMysqlScheme", "true");
        String url = dsn.startsWith("jdbc:mysql:") ? dsn + (dsn.contains("?") ? "&" : "?") + "permitMysqlScheme=true" : dsn;
        Connection connection = DriverManager.getConnection(url, properties);
        if (sqlite()) try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON"); statement.execute("PRAGMA busy_timeout=6000");
        } catch (SQLException e) { connection.close(); throw e; }
        return connection;
    }
    @Override public String toString() { return "JDBC[" + (sqlite() ? "SQLite" : "MySQL/MariaDB") + ", prefix=" + prefix + "]"; }
}
