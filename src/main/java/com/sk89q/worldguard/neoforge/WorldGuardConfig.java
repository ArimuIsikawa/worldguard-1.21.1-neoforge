package com.sk89q.worldguard.neoforge;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.*;
import com.sk89q.worldguard.neoforge.storage.JdbcSettings;

/** Original YAML keys are retained; rules use per-dimension overrides. */
public final class WorldGuardConfig {
    private final Path directory;
    private final Map<String, Object> global;
    private final Map<String, Map<String, Object>> worlds = new HashMap<>();
    private static final Map<String, String> DISABLE_FLAGS = Map.ofEntries(
        Map.entry("fire-spread", "fire.disable-all-fire-spread"),
        Map.entry("lava-fire", "fire.disable-lava-fire-spread"),
        Map.entry("lighter", "ignition.block-lighter"),
        Map.entry("tnt", "ignition.block-tnt"),
        Map.entry("creeper-explosion", "mobs.block-creeper-explosions"),
        Map.entry("wither-damage", "mobs.block-wither-explosions"),
        Map.entry("ghast-fireball", "mobs.block-fireball-explosions"),
        Map.entry("enderdragon-block-damage", "mobs.block-enderdragon-block-damage"),
        Map.entry("enderman-grief", "mobs.block-enderman-griefing"),
        Map.entry("snowman-trails", "mobs.block-snowman-trails"),
        Map.entry("mob-damage", "player-damage.disable-mob-damage"),
        Map.entry("fall-damage", "player-damage.disable-fall-damage"),
        Map.entry("natural-health-regen", "default.disable-health-regain"),
        Map.entry("exp-drops", "protection.disable-xp-orb-drops"),
        Map.entry("mushroom-growth", "dynamics.disable-mushroom-spread"),
        Map.entry("ice-melt", "dynamics.disable-ice-melting"),
        Map.entry("snow-melt", "dynamics.disable-snow-melting"),
        Map.entry("snow-fall", "dynamics.disable-snow-formation"),
        Map.entry("ice-form", "dynamics.disable-ice-formation"),
        Map.entry("leaf-decay", "dynamics.disable-leaf-decay"),
        Map.entry("grass-growth", "dynamics.disable-grass-growth"),
        Map.entry("mycelium-spread", "dynamics.disable-mycelium-spread"),
        Map.entry("vine-growth", "dynamics.disable-vine-growth"),
        Map.entry("rock-growth", "dynamics.disable-rock-growth"),
        Map.entry("sculk-growth", "dynamics.disable-sculk-growth"),
        Map.entry("crop-growth", "dynamics.disable-crop-growth"),
        Map.entry("soil-dry", "dynamics.disable-soil-dehydration"),
        Map.entry("moisture-change", "dynamics.disable-soil-moisture-change"),
        Map.entry("coral-fade", "dynamics.disable-coral-block-fade"),
        Map.entry("copper-fade", "dynamics.disable-copper-block-fade"),
        Map.entry("lava-harden", "dynamics.disable-lava-harden")
    );

    public WorldGuardConfig(Path directory) throws IOException {
        this.directory = directory;
        global = read(directory.resolve("config.yml"));
        int level = integer(null, "operator-level", 2);
        if (level < 0 || level > 4) throw new IOException("operator-level must be 0..4");
        if (bool(null, "regions.sql.use", false)) try { sqlSettings(); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid WorldGuard SQL configuration", e); }
    }

    public static Map<String, Object> read(Path file) throws IOException {
        if (!Files.exists(file)) return new LinkedHashMap<>();
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        options.setCodePointLimit(16 * 1024 * 1024);
        try (Reader reader = Files.newBufferedReader(file)) {
            Object value = new Yaml(new SafeConstructor(options)).load(reader);
            if (value == null) return new LinkedHashMap<>();
            if (!(value instanceof Map<?, ?> map)) throw new IOException("Expected YAML mapping: " + file);
            Map<String, Object> result = new LinkedHashMap<>();
            for (var entry : map.entrySet()) result.put(String.valueOf(entry.getKey()), entry.getValue());
            return result;
        } catch (RuntimeException e) { throw new IOException("Invalid YAML configuration: " + file, e); }
    }

    public static Path worldDirectory(Path root, String dimension) {
        if (!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid dimension: " + dimension);
        String[] parts = dimension.split(":", 2);
        if (parts[0].equals(".") || parts[0].equals("..")) throw new IllegalArgumentException("Invalid dimension namespace");
        for (String segment : parts[1].split("/", -1))
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException("Invalid dimension path");
        Path result = root.resolve(parts[0]).resolve(parts[1]).normalize();
        if (!result.startsWith(root.normalize())) throw new IllegalArgumentException("Dimension escapes world directory");
        return result;
    }

    public void loadWorld(String dimension) throws IOException {
        worlds.put(dimension, read(worldDirectory(directory.resolve("worlds"), dimension).resolve("config.yml")));
    }

    private static Object lookup(Map<String, Object> map, String key) {
        Object value = map;
        for (String part : key.split("\\.")) {
            if (!(value instanceof Map<?, ?> nested)) return null;
            value = nested.get(part);
        }
        return value;
    }

    public Object value(String world, String key) {
        Object value = lookup(worlds.getOrDefault(world, Map.of()), key);
        return value != null ? value : lookup(global, key);
    }
    public boolean bool(String world, String key, boolean fallback) {
        Object value = value(world, key);
        return value == null ? fallback : value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString());
    }
    public int integer(String world, String key, int fallback) {
        Object value = value(world, key);
        if (value == null) return fallback;
        try { return value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid integer " + key, e); }
    }
    public List<String> strings(String world, String key) {
        Object value = value(world, key);
        if (value instanceof Collection<?> c) return c.stream().map(Object::toString).toList();
        return value == null ? List.of() : List.of(value.toString());
    }
    public String string(String world, String key, String fallback) {
        Object value = value(world, key);
        return value == null ? fallback : value.toString();
    }
    public int operatorLevel() { return integer(null, "operator-level", 2); }
    public void setGlobal(String path, Object value) throws IOException {
        Map<String,Object> copy=new LinkedHashMap<>(global),current=copy;
        String[] parts=path.split("\\.");
        for(int index=0;index<parts.length-1;index++) {
            Map<String,Object> child=new LinkedHashMap<>();
            if(current.get(parts[index]) instanceof Map<?,?> existing) existing.forEach((k,v)->child.put(k.toString(),v));
            current.put(parts[index],child); current=child;
        }
        current.put(parts[parts.length-1],value);
        Path target=directory.resolve("config.yml"),temporary=Files.createTempFile(directory,"config-update-",".tmp");
        try {
            Files.writeString(temporary,new Yaml().dump(copy));
            try { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
            global.clear(); global.putAll(copy);
        } finally { Files.deleteIfExists(temporary); }
    }
    public boolean defaultBypass() { return !bool(null, "regions.disable-bypass-by-default", false); }
    public boolean regionsEnabled(String world) { return bool(world, "regions.enable", true); }
    public JdbcSettings sqlSettings() {
        return new JdbcSettings(string(null, "regions.sql.dsn", "jdbc:mysql://localhost/worldguard"),
                string(null, "regions.sql.username", "worldguard"), string(null, "regions.sql.password", ""), string(null, "regions.sql.table-prefix", ""));
    }
    public Map<String, String> worldAliases() {
        Map<String, String> aliases = new LinkedHashMap<>(Map.of("world", "minecraft:overworld", "world_nether", "minecraft:the_nether", "world_the_end", "minecraft:the_end"));
        aliases.putAll(migrationWorlds()); return aliases;
    }
    public boolean denied(String world, String flag) {
        String key = DISABLE_FLAGS.get(flag);
        return key != null && bool(world, key, false);
    }
    public Map<String, String> migrationWorlds() {
        Object value = value(null, "migration.worlds");
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> m) m.forEach((k, v) -> result.put(k.toString(), v.toString()));
        return result;
    }
    public Map<String,Object> diagnosticSnapshot() {
        Map<String,Object> dimensions=new LinkedHashMap<>();
        worlds.forEach((world,configuration)->dimensions.put(world,redacted(configuration)));
        return Map.of("global",redacted(global),"worlds",dimensions);
    }
    private static Object redacted(Object value) {
        if(value instanceof Map<?,?> map) {
            Map<String,Object> result=new LinkedHashMap<>();
            map.forEach((key,child)->{
                String name=key.toString(),lower=name.toLowerCase(Locale.ROOT);
                boolean privateValue=lower.contains("password") || lower.equals("pass") || lower.equals("dsn") || lower.equals("host-keys") || lower.contains("secret") || lower.contains("token");
                result.put(name,privateValue ? "[redacted]" : redacted(child));
            }); return result;
        }
        if(value instanceof Collection<?> values) return values.stream().map(WorldGuardConfig::redacted).toList();
        return value;
    }
}
