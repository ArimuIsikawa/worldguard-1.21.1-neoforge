package com.sk89q.worldguard.neoforge.storage;

import com.sk89q.worldguard.neoforge.region.Domain;
import com.sk89q.worldguard.neoforge.region.FlagRegistry;
import com.sk89q.worldguard.neoforge.region.Region;
import com.sk89q.worldguard.neoforge.region.RegionRepository;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/** WorldGuard 7 relational schema, with whole-dimension transactional writes. */
public final class SqlRegionStorage {
    private final JdbcSettings settings;
    private final Map<String, String> worldAliases;
    public SqlRegionStorage(JdbcSettings settings, Map<String, String> worldAliases) throws IOException {
        this.settings = settings;
        this.worldAliases = new LinkedHashMap<>(worldAliases);
        initialize();
    }
    public JdbcSettings settings() { return settings; }
    private String table(String suffix) { return settings.table(suffix); }
    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(16 * 1024 * 1024); options.setMaxAliasesForCollections(50);
        return new Yaml(new SafeConstructor(options));
    }
    private void initialize() throws IOException {
        String id = settings.sqlite() ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY";
        String text = settings.sqlite() ? "TEXT" : "VARCHAR(128) COLLATE utf8mb4_bin";
        String groupText = settings.sqlite() ? "TEXT" : "VARCHAR(64) COLLATE utf8mb4_bin";
        String engine = settings.sqlite() ? "" : " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin";
        try (Connection c = settings.open(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("world") + " (id " + id + ",name " + text + " NOT NULL UNIQUE)" + engine);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("user") + " (id " + id + ",name " + groupText + " UNIQUE,uuid VARCHAR(36) UNIQUE)" + engine);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("group") + " (id " + id + ",name " + groupText + " NOT NULL UNIQUE)" + engine);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("region") + " (id " + text + " NOT NULL,world_id INTEGER NOT NULL,type VARCHAR(16) NOT NULL,priority INTEGER NOT NULL,parent " + text + ",PRIMARY KEY(id,world_id))" + engine);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("region_cuboid") + " (region_id " + text + " NOT NULL,world_id INTEGER NOT NULL,min_x INTEGER NOT NULL,min_y INTEGER NOT NULL,min_z INTEGER NOT NULL,max_x INTEGER NOT NULL,max_y INTEGER NOT NULL,max_z INTEGER NOT NULL,PRIMARY KEY(region_id,world_id))" + engine);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("region_poly2d") + " (region_id " + text + " NOT NULL,world_id INTEGER NOT NULL,min_y INTEGER NOT NULL,max_y INTEGER NOT NULL,PRIMARY KEY(region_id,world_id))" + engine);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("region_poly2d_point") + " (id " + id + ",region_id " + text + " NOT NULL,world_id INTEGER NOT NULL,x INTEGER NOT NULL,z INTEGER NOT NULL)" + engine);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("region_flag") + " (id " + id + ",region_id " + text + " NOT NULL,world_id INTEGER NOT NULL,flag " + text + " NOT NULL,value " + (settings.sqlite() ? "TEXT" : "LONGTEXT") + " NOT NULL)" + engine);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("region_players") + " (region_id " + text + " NOT NULL,world_id INTEGER NOT NULL,user_id INTEGER NOT NULL,owner BOOLEAN NOT NULL,PRIMARY KEY(region_id,world_id,user_id,owner))" + engine);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("region_groups") + " (region_id " + text + " NOT NULL,world_id INTEGER NOT NULL,group_id INTEGER NOT NULL,owner BOOLEAN NOT NULL,PRIMARY KEY(region_id,world_id,group_id,owner))" + engine);
            // WorldGuard's pre-UUID schema must not silently discard UUID identities.
            try (ResultSet columns = c.getMetaData().getColumns(c.getCatalog(), null, settings.prefix() + "user", "uuid")) {
                boolean found=false;
                while(columns.next()) found|=(settings.prefix()+"user").equalsIgnoreCase(columns.getString("TABLE_NAME"));
                if (!found) {
                    s.executeUpdate("ALTER TABLE " + table("user") + " ADD COLUMN uuid VARCHAR(36)");
                    if(!settings.sqlite()) {
                        s.executeUpdate("ALTER TABLE " + table("user") + " MODIFY name VARCHAR(64) NULL");
                        s.executeUpdate("CREATE UNIQUE INDEX `" + settings.prefix() + "user_uuid` ON " + table("user") + " (uuid)");
                    }
                }
            }
            if (!settings.sqlite()) {
                try (ResultSet columns = c.getMetaData().getColumns(c.getCatalog(), null, settings.prefix() + "region", "priority")) {
                    while(columns.next()) if ((settings.prefix()+"region").equalsIgnoreCase(columns.getString("TABLE_NAME")) && columns.getInt("DATA_TYPE") == Types.SMALLINT)
                        s.executeUpdate("ALTER TABLE " + table("region") + " MODIFY priority INTEGER NOT NULL");
                }
                // Original parent FK references only the ID, incorrectly crossing world boundaries.
                List<String> parentKeys = new ArrayList<>();
                try (ResultSet keys = c.getMetaData().getImportedKeys(c.getCatalog(), null, settings.prefix() + "region")) {
                    while (keys.next()) if ("parent".equals(keys.getString("FKCOLUMN_NAME"))) parentKeys.add(keys.getString("FK_NAME"));
                }
                for (String key : new HashSet<>(parentKeys)) {
                    if (!key.matches("[A-Za-z0-9_]+")) throw new SQLException("Unexpected parent constraint name");
                    s.executeUpdate("ALTER TABLE " + table("region") + " DROP FOREIGN KEY `" + key + "`");
                }
            }
            fixDomainKey(c,"region_groups","group_id");
            fixDomainKey(c,"region_players","user_id");
            if(!settings.sqlite()) ensureMigrationBaseline(c);
        } catch (SQLException e) { throw failure("initialize", e); }
    }
    private void fixDomainKey(Connection c,String suffix,String identity) throws SQLException {
        boolean includesOwner = false;
        try (ResultSet keys = c.getMetaData().getPrimaryKeys(c.getCatalog(), null, settings.prefix() + suffix)) {
            while (keys.next()) includesOwner |= "owner".equalsIgnoreCase(keys.getString("COLUMN_NAME"));
        }
        if (includesOwner) return;
        try (Statement s = c.createStatement()) {
            if (!settings.sqlite()) {
                s.executeUpdate("ALTER TABLE " + table(suffix) + " DROP PRIMARY KEY,ADD PRIMARY KEY(region_id,world_id," + identity + ",owner)");
            } else {
                c.setAutoCommit(false);
                try {
                    s.executeUpdate("CREATE TABLE " + table(suffix+"_upgrade") + " (region_id TEXT NOT NULL,world_id INTEGER NOT NULL," + identity + " INTEGER NOT NULL,owner BOOLEAN NOT NULL,PRIMARY KEY(region_id,world_id," + identity + ",owner))");
                    s.executeUpdate("INSERT INTO " + table(suffix+"_upgrade") + " SELECT region_id,world_id," + identity + ",owner FROM " + table(suffix));
                    s.executeUpdate("DROP TABLE " + table(suffix));
                    s.executeUpdate("ALTER TABLE " + table(suffix+"_upgrade") + " RENAME TO `" + settings.prefix() + suffix + "`");
                    c.commit();
                } catch (SQLException e) { c.rollback(); throw e; }
                finally { c.setAutoCommit(true); }
            }
        }
    }
    /** A fresh native database must not re-run WorldGuard's already applied V1/V2 scripts. */
    private void ensureMigrationBaseline(Connection c) throws SQLException {
        try(Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("migrations") + " (version_rank INTEGER NOT NULL,installed_rank INTEGER NOT NULL,version VARCHAR(50) NOT NULL PRIMARY KEY,description VARCHAR(200) NOT NULL,type VARCHAR(20) NOT NULL,script VARCHAR(1000) NOT NULL,checksum INTEGER,installed_by VARCHAR(100) NOT NULL,installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,execution_time INTEGER NOT NULL,success BOOLEAN NOT NULL) ENGINE=InnoDB");
            try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM " + table("migrations"))) {
                if(r.next() && r.getInt(1)>0) return;
            }
            s.executeUpdate("INSERT INTO " + table("migrations") + " (version_rank,installed_rank,version,description,type,script,installed_by,execution_time,success) VALUES (1,1,'2','WorldGuard compatible schema','INIT','WorldGuard compatible schema',CURRENT_USER(),0,true)");
        }
    }
    public Set<String> dimensions() throws IOException {
        Set<String> result = new LinkedHashSet<>();
        try (Connection c = settings.open(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT name FROM " + table("world"))) {
            while (r.next()) {
                String dimension = worldAliases.getOrDefault(r.getString(1), r.getString(1));
                if (dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) result.add(dimension);
            }
            return result;
        } catch (SQLException e) { throw failure("list worlds", e); }
    }
    private Integer worldId(Connection c, String dimension, boolean create) throws SQLException {
        LinkedHashSet<String> names = new LinkedHashSet<>(); names.add(dimension);
        worldAliases.forEach((legacy, target) -> { if (target.equals(dimension)) names.add(legacy); });
        Integer found = null;
        try (PreparedStatement p = c.prepareStatement("SELECT id FROM " + table("world") + " WHERE name=?")) {
            for (String name : names) {
                p.setString(1, name);
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) {
                        if (found != null && found != r.getInt(1)) throw new SQLException("Multiple SQL worlds map to one dimension; provide unambiguous migration.worlds");
                        found = r.getInt(1);
                    }
                }
            }
        }
        if (found != null || !create) return found;
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + table("world") + " (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, dimension); p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) { if (r.next()) return r.getInt(1); }
        }
        throw new SQLException("No generated SQL world ID");
    }
    public Map<String, Region> load(String dimension) throws IOException {
        try (Connection c = settings.open()) {
            c.setAutoCommit(false);
            try {
                Map<String, Region> loaded = load(c, dimension);
                RegionRepository.validate(loaded); c.commit(); return loaded;
            } catch (SQLException | IOException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException | RuntimeException e) { throw failure("load regions", e); }
    }
    private Map<String, Region> load(Connection c, String dimension) throws SQLException, IOException {
        Map<String, Region> regions = new LinkedHashMap<>(); Integer world = worldId(c, dimension, false);
        if (world == null) return regions;
        Map<String, int[]> cuboids = new HashMap<>(), polygons = new HashMap<>();
        Map<String, List<Region.Point>> points = new HashMap<>();
        try (ResultSet r = rows(c, "SELECT * FROM " + table("region_cuboid") + " WHERE world_id=?", world)) {
            while (r.next()) cuboids.put(r.getString("region_id"), new int[]{integer(r,"min_x"),integer(r,"min_y"),integer(r,"min_z"),integer(r,"max_x"),integer(r,"max_y"),integer(r,"max_z")});
        }
        try (ResultSet r = rows(c, "SELECT * FROM " + table("region_poly2d") + " WHERE world_id=?", world)) {
            while (r.next()) polygons.put(r.getString("region_id"), new int[]{integer(r,"min_y"),integer(r,"max_y")});
        }
        try (ResultSet r = rows(c, "SELECT * FROM " + table("region_poly2d_point") + " WHERE world_id=? ORDER BY id", world)) {
            while (r.next()) points.computeIfAbsent(r.getString("region_id"), ignored -> new ArrayList<>()).add(new Region.Point(integer(r,"x"),integer(r,"z")));
        }
        try (ResultSet r = rows(c, "SELECT * FROM " + table("region") + " WHERE world_id=? ORDER BY id", world)) {
            while (r.next()) {
                String id = r.getString("id"); int[] box = cuboids.get(id), heights = polygons.get(id);
                Region region = switch (r.getString("type")) {
                    case "global" -> Region.global(id);
                    case "cuboid" -> { if (box == null) throw new SQLException("Missing cuboid geometry for " + id); yield Region.cuboid(id,box[0],box[1],box[2],box[3],box[4],box[5]); }
                    case "poly2d" -> { if (heights == null) throw new SQLException("Missing polygon geometry for " + id); yield Region.polygon(id,points.getOrDefault(id,List.of()),heights[0],heights[1]); }
                    default -> throw new SQLException("Unsupported SQL region type");
                };
                region.priority(integer(r,"priority")); region.parent(r.getString("parent"));
                if (regions.put(region.id(), region) != null) throw new SQLException("Duplicate normalized SQL region ID");
            }
        }
        try (ResultSet r = rows(c, "SELECT * FROM " + table("region_flag") + " WHERE world_id=? ORDER BY id", world)) {
            while (r.next()) {
                Region region = regions.get(r.getString("region_id").toLowerCase(Locale.ROOT)); if (region == null) continue;
                String name = r.getString("flag"), known = FlagRegistry.canonicalName(name);
                Object value = yaml().load(r.getString("value"));
                value = known == null ? value : FlagRegistry.normalize(known, value);
                if (value != null) region.flags().put(known == null ? name : known, value);
            }
        }
        try (ResultSet r = rows(c, "SELECT p.region_id,p.owner,u.name,u.uuid FROM " + table("region_players") + " p JOIN " + table("user") + " u ON p.user_id=u.id WHERE p.world_id=?", world)) {
            while (r.next()) {
                Region region = regions.get(r.getString("region_id").toLowerCase(Locale.ROOT)); if (region == null) continue;
                Domain domain = r.getBoolean("owner") ? region.owners() : region.members();
                if (r.getString("uuid") != null) domain.uuids().add(UUID.fromString(r.getString("uuid")));
                else if (r.getString("name") != null) domain.players().add(r.getString("name"));
            }
        }
        try (ResultSet r = rows(c, "SELECT p.region_id,p.owner,g.name FROM " + table("region_groups") + " p JOIN " + table("group") + " g ON p.group_id=g.id WHERE p.world_id=?", world)) {
            while (r.next()) { Region region = regions.get(r.getString("region_id").toLowerCase(Locale.ROOT)); if (region != null) (r.getBoolean("owner") ? region.owners() : region.members()).groups().add(r.getString("name")); }
        }
        return regions;
    }
    private static int integer(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column); if (r.wasNull() || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new SQLException("Invalid integer in SQL region " + column);
        return (int) value;
    }
    /** closeOnCompletion ensures closing each result also closes its prepared statement. */
    private static ResultSet rows(Connection c, String sql, int world) throws SQLException {
        PreparedStatement p = c.prepareStatement(sql);
        try { p.closeOnCompletion(); p.setInt(1, world); return p.executeQuery(); }
        catch (SQLException e) { p.close(); throw e; }
    }
    public void save(String dimension, Map<String, Region> regions) throws IOException {
        RegionRepository.validate(regions);
        try (Connection c = settings.open()) {
            c.setAutoCommit(false);
            try { save(c, dimension, regions); c.commit(); }
            catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException | RuntimeException e) { throw failure("save regions (transaction rolled back)", e); }
    }
    private void save(Connection c, String dimension, Map<String, Region> regions) throws SQLException {
        int world = worldId(c, dimension, true);
        if (!settings.sqlite()) try (ResultSet ignored = rows(c, "SELECT id FROM " + table("world") + " WHERE id=? FOR UPDATE", world)) { ignored.next(); }
        for (String suffix : List.of("region_poly2d_point","region_cuboid","region_poly2d","region_flag","region_players","region_groups")) execute(c,"DELETE FROM " + table(suffix) + " WHERE world_id=?",world);
        execute(c,"UPDATE " + table("region") + " SET parent=NULL WHERE world_id=?",world);
        execute(c,"DELETE FROM " + table("region") + " WHERE world_id=?",world);
        for (Region r : regions.values()) execute(c,"INSERT INTO " + table("region") + " (id,world_id,type,priority,parent) VALUES (?,?,?,?,?)",r.id(),world,r.type(),r.priority(),null);
        Yaml yaml = yaml();
        for (Region r : regions.values()) {
            if (r.parent() != null) execute(c,"UPDATE " + table("region") + " SET parent=? WHERE id=? AND world_id=?",r.parent(),r.id(),world);
            if (r.type().equals("cuboid")) execute(c,"INSERT INTO " + table("region_cuboid") + " (region_id,world_id,min_x,min_y,min_z,max_x,max_y,max_z) VALUES (?,?,?,?,?,?,?,?)",r.id(),world,r.minX(),r.minY(),r.minZ(),r.maxX(),r.maxY(),r.maxZ());
            if (r.type().equals("poly2d")) {
                execute(c,"INSERT INTO " + table("region_poly2d") + " (region_id,world_id,min_y,max_y) VALUES (?,?,?,?)",r.id(),world,r.minY(),r.maxY());
                for (Region.Point p : r.points()) execute(c,"INSERT INTO " + table("region_poly2d_point") + " (region_id,world_id,x,z) VALUES (?,?,?,?)",r.id(),world,p.x(),p.z());
            }
            for (var flag : r.flags().entrySet()) {
                Object value = FlagRegistry.contains(flag.getKey()) ? FlagRegistry.normalize(flag.getKey(), flag.getValue()) : flag.getValue();
                if (value != null) execute(c,"INSERT INTO " + table("region_flag") + " (region_id,world_id,flag,value) VALUES (?,?,?,?)",r.id(),world,flag.getKey(),yaml.dump(value));
            }
            domain(c,world,r.id(),r.owners(),true); domain(c,world,r.id(),r.members(),false);
        }
    }
    private void domain(Connection c, int world, String region, Domain domain, boolean owner) throws SQLException {
        for (UUID uuid : domain.uuids()) execute(c,"INSERT INTO " + table("region_players") + " (region_id,world_id,user_id,owner) VALUES (?,?,?,?)",region,world,identity(c,"user","uuid",uuid.toString()),owner);
        for (String name : domain.players()) execute(c,"INSERT INTO " + table("region_players") + " (region_id,world_id,user_id,owner) VALUES (?,?,?,?)",region,world,identity(c,"user","name",name),owner);
        for (String group : domain.groups()) execute(c,"INSERT INTO " + table("region_groups") + " (region_id,world_id,group_id,owner) VALUES (?,?,?,?)",region,world,identity(c,"group","name",group),owner);
    }
    private int identity(Connection c, String suffix, String column, String value) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id FROM " + table(suffix) + " WHERE " + column + "=?")) {
            p.setString(1,value); try (ResultSet r = p.executeQuery()) { if (r.next()) return r.getInt(1); }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + table(suffix) + " (" + column + ") VALUES (?)",Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1,value); p.executeUpdate(); try (ResultSet r = p.getGeneratedKeys()) { if (r.next()) return r.getInt(1); }
        }
        throw new SQLException("No generated SQL domain ID");
    }
    private static void execute(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) { for (int i=0;i<values.length;i++) p.setObject(i+1,values[i]); p.executeUpdate(); }
    }
    private static IOException failure(String operation, Exception e) {
        return new IOException("Unable to " + operation + " in WorldGuard SQL storage", e);
    }
}
