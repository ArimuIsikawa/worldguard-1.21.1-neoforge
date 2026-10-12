package com.sk89q.worldguard.neoforge.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;

/** Compatible with WorldGuard's event/world/player/x/y/z/item/time/comment log table. */
public final class SqlBlacklistLogger {
    private final JdbcSettings settings;
    private final String table;
    private boolean initialized;
    public SqlBlacklistLogger(JdbcSettings settings, String table) {
        this.settings = settings;
        this.table = settings.table(table);
    }
    public synchronized void log(String event, String world, String player, int x, int y, int z, String item, String comment) throws SQLException {
        try (Connection connection = settings.open()) {
            if (!initialized) {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table
                            + " (event VARCHAR(32) NOT NULL,world VARCHAR(128) NOT NULL,player VARCHAR(64) NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,item VARCHAR(128) NOT NULL,time BIGINT NOT NULL,comment TEXT)");
                }
                initialized = true;
            }
            try (var p = connection.prepareStatement("INSERT INTO " + table + " (event,world,player,x,y,z,item,time,comment) VALUES (?,?,?,?,?,?,?,?,?)")) {
                p.setString(1,event.replaceFirst("^on-", "").replace('-','_').toUpperCase(Locale.ROOT));
                p.setString(2,world); p.setString(3,player == null ? "" : player); p.setInt(4,x); p.setInt(5,y); p.setInt(6,z);
                p.setString(7,item); p.setLong(8,Instant.now().getEpochSecond()); p.setString(9,comment); p.executeUpdate();
            }
        }
    }
}
