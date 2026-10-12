package com.sk89q.worldguard.neoforge.region;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;
import com.sk89q.worldguard.neoforge.storage.SqlRegionStorage;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** WorldGuard-compatible YAML storage. Bad files never replace a loaded region set. */
public final class RegionRepository {
    private final Path root;
    private final Map<String, Map<String, Region>> worlds = new LinkedHashMap<>();
    private final Map<String, Long> revisions = new HashMap<>();
    private final Set<String> failedLoads = new HashSet<>();
    private SqlRegionStorage sql;

    public RegionRepository(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public RegionRepository(Path root, SqlRegionStorage sql) { this(root); this.sql = sql; }
    public Path root() { return root; }
    public synchronized boolean isSql() { return sql != null; }
    public synchronized Set<String> storedDimensions() throws IOException {
        if (sql != null) return sql.dimensions();
        Set<String> found = new HashSet<>();
        if (Files.exists(root)) try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().equals("regions.yml") && Files.isRegularFile(p)).toList()) {
                Path relative = root.relativize(path.getParent());
                if (relative.getNameCount() >= 2) {
                    String dimension = relative.getName(0) + ":" + relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
                    file(dimension); found.add(dimension);
                }
            }
        }
        return found;
    }

    public Path file(String dimension) {
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid dimension ID: " + dimension);
        }
        String[] parts = dimension.split(":", 2);
        for (String segment : parts[1].split("/")) {
            if (segment.equals("..") || segment.equals(".") || segment.isEmpty()) throw new IllegalArgumentException("Invalid dimension path");
        }
        Path result = root.resolve(parts[0]).resolve(parts[1]).resolve("regions.yml").normalize();
        if (!result.startsWith(root)) throw new IllegalArgumentException("Dimension path escapes storage root");
        return result;
    }

    public synchronized Map<String, Region> regions(String dimension) throws IOException {
        if (!worlds.containsKey(dimension)) reload(dimension);
        return worlds.get(dimension);
    }

    public synchronized long revision(String dimension) { return revisions.getOrDefault(dimension, 0L); }
    public synchronized Set<String> allDimensions() { return Collections.unmodifiableSet(new HashSet<>(worlds.keySet())); }

    public synchronized void reload(String dimension) throws IOException {
        Path path = file(dimension);
        Map<String, Region> loaded;
        try { loaded = sql != null ? sql.load(dimension) : Files.exists(path) ? read(path) : new LinkedHashMap<>(); }
        catch (IOException exception) { failedLoads.add(dimension); throw exception; }
        Map<String, Region> existing = worlds.get(dimension);
        if (existing == null) worlds.put(dimension, loaded);
        else { existing.clear(); existing.putAll(loaded); }
        failedLoads.remove(dimension);
        changed(dimension);
    }

    public synchronized void save(String dimension) throws IOException {
        if (failedLoads.contains(dimension)) throw new IOException("Refusing to overwrite regions after a failed load: " + dimension);
        Map<String, Region> regions = regions(dimension);
        if (sql == null) write(file(dimension), regions); else sql.save(dimension, regions);
        changed(dimension);
    }

    public synchronized void saveAll() throws IOException {
        IOException failure = null;
        for (String dimension : new ArrayList<>(worlds.keySet())) {
            try { save(dimension); }
            catch (IOException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    /** Stage every requested world before publishing any of the reload results. */
    public synchronized void reloadAll(Collection<String> dimensions) throws IOException {
        reloadAll(dimensions, sql);
    }
    public synchronized void reloadAll(Collection<String> dimensions, SqlRegionStorage nextSql) throws IOException {
        Map<String, Map<String, Region>> staged = new LinkedHashMap<>();
        for (String dimension : dimensions) {
            Path path = file(dimension);
            try { staged.put(dimension, nextSql != null ? nextSql.load(dimension) : Files.exists(path) ? read(path) : new LinkedHashMap<>()); }
            catch (IOException exception) { failedLoads.add(dimension); throw exception; }
        }
        sql = nextSql;
        for (var entry : staged.entrySet()) {
            Map<String, Region> existing = worlds.get(entry.getKey());
            if (existing == null) worlds.put(entry.getKey(), entry.getValue());
            else { existing.clear(); existing.putAll(entry.getValue()); }
            failedLoads.remove(entry.getKey());
            changed(entry.getKey());
        }
    }

    /** Merge by default; replace=true replaces the complete dimension after validation. */
    public synchronized int importYaml(Path source, String dimension, boolean replace) throws IOException {
        Map<String, Region> imported = read(source, false);
        // Explicit replacing imports can repair a broken native file without loading it.
        Map<String, Region> current = replace ? worlds.get(dimension) : regions(dimension);
        Map<String, Region> candidate = new LinkedHashMap<>();
        if (!replace) candidate.putAll(current);
        int importedCount = 0;
        for (Map.Entry<String, Region> entry : imported.entrySet()) {
            if (replace || !candidate.containsKey(entry.getKey())) {
                candidate.put(entry.getKey(), entry.getValue());
                importedCount++;
            }
        }
        validate(candidate);
        if (sql == null) write(file(dimension), candidate); else sql.save(dimension, candidate);
        if (current == null) worlds.put(dimension, candidate);
        else { current.clear(); current.putAll(candidate); }
        failedLoads.remove(dimension);
        changed(dimension);
        return importedCount;
    }

    private void changed(String dimension) { revisions.merge(dimension, 1L, Long::sum); }
    public synchronized void exportYaml(String dimension, Path target) throws IOException { write(target, regions(dimension)); }
    public synchronized void replace(String dimension, Map<String, Region> replacement) throws IOException {
        replacement = new LinkedHashMap<>(replacement);
        validate(replacement);
        Map<String, Region> current = regions(dimension);
        if (sql == null) write(file(dimension), replacement); else sql.save(dimension, replacement);
        current.clear(); current.putAll(replacement); failedLoads.remove(dimension); changed(dimension);
    }

    private static Yaml yaml() {
        LoaderOptions loader = new LoaderOptions();
        loader.setAllowDuplicateKeys(false);
        loader.setMaxAliasesForCollections(50);
        loader.setNestingDepthLimit(80);
        loader.setCodePointLimit(16 * 1024 * 1024);
        DumperOptions dumper = new DumperOptions();
        dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumper.setIndent(4);
        dumper.setPrettyFlow(true);
        return new Yaml(new SafeConstructor(loader), new Representer(dumper), dumper, loader);
    }

    private static Map<String, Region> read(Path source) throws IOException {
        return read(source, true);
    }

    private static Map<String, Region> read(Path source, boolean validateParents) throws IOException {
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            Object document = yaml().load(reader);
            Map<?, ?> root = map(document, "document");
            if (!root.containsKey("regions")) throw new IllegalArgumentException("Missing regions section");
            Map<?, ?> entries = map(root.get("regions"), "regions");
            Map<String, Region> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : entries.entrySet()) {
                String id = Region.normalizeId(string(entry.getKey(), "region ID"));
                if (result.containsKey(id)) throw new IllegalArgumentException("Duplicate region ID: " + id);
                Map<?, ?> data = map(entry.getValue(), "region " + id);
                String type = string(data.get("type"), "region type");
                Region region = switch (type) {
                    case "global" -> Region.global(id);
                    case "cuboid" -> {
                        Map<?, ?> min = map(data.get("min"), "min"), max = map(data.get("max"), "max");
                        yield Region.cuboid(id, coordinate(min, "x"), coordinate(min, "y"), coordinate(min, "z"),
                                coordinate(max, "x"), coordinate(max, "y"), coordinate(max, "z"));
                    }
                    case "poly2d" -> {
                        if (!(data.get("points") instanceof Collection<?> rawPoints)) throw new IllegalArgumentException("Missing polygon points");
                        List<Region.Point> points = new ArrayList<>();
                        for (Object rawPoint : rawPoints) {
                            Map<?, ?> point = map(rawPoint, "polygon point");
                            points.add(new Region.Point(coordinate(point, "x"), coordinate(point, "z")));
                        }
                        yield Region.polygon(id, points, coordinate(data, "min-y"), coordinate(data, "max-y"));
                    }
                    default -> throw new IllegalArgumentException("Unknown region type " + type + " for " + id);
                };
                region.priority(data.containsKey("priority") ? FlagRegistry.integer(data.get("priority")) : 0);
                if (data.get("parent") != null) region.parent(string(data.get("parent"), "parent"));
                if (data.get("flags") != null) {
                    map(data.get("flags"), "flags").forEach((rawName, value) -> {
                        String name = string(rawName, "flag name");
                        String known = FlagRegistry.canonicalName(name);
                        Object normalized = known == null ? safeValue(value) : FlagRegistry.normalize(known, value);
                        if (normalized != null) region.flags().put(known == null ? name : known, normalized);
                    });
                }
                loadDomain(data.get("owners"), region.owners());
                loadDomain(data.get("members"), region.members());
                result.put(id, region);
            }
            if (validateParents) validate(result);
            return result;
        } catch (RuntimeException exception) {
            throw new IOException("Cannot load regions from " + source + ": " + exception.getMessage(), exception);
        }
    }

    /** Validate relationships before writing or applying a command transaction. */
    public static void validate(Map<String, Region> regions) throws IOException {
        try {
            for (Map.Entry<String, Region> entry : regions.entrySet()) {
                Region region = entry.getValue();
                if (region == null || !entry.getKey().equals(region.id())) throw new IllegalArgumentException("Region map ID mismatch");
                Set<String> seen = new HashSet<>();
                for (Region current = region; current != null; current = current.parent() == null ? null : regions.get(current.parent())) {
                    if (!seen.add(current.id())) throw new IllegalArgumentException("Parent cycle at " + current.id());
                    if (current.parent() != null && !regions.containsKey(current.parent())) throw new IllegalArgumentException("Missing parent " + current.parent() + " for " + current.id());
                }
                region.flags().forEach((name, value) -> {
                    if (FlagRegistry.contains(name)) FlagRegistry.normalize(name, value); else safeValue(value);
                });
            }
        } catch (RuntimeException exception) {
            throw new IOException("Invalid region data: " + exception.getMessage(), exception);
        }
    }

    private static void loadDomain(Object value, Domain domain) {
        if (value == null) return;
        Map<?, ?> data = map(value, "domain");
        for (String name : strings(data.get("players"), "players")) domain.players().add(name);
        for (String uuid : strings(data.get("unique-ids"), "unique-ids")) domain.uuids().add(UUID.fromString(uuid));
        for (String name : strings(data.get("groups"), "groups")) domain.groups().add(name);
    }

    private static List<String> strings(Object value, String field) {
        if (value == null) return List.of();
        if (!(value instanceof Collection<?> collection)) throw new IllegalArgumentException(field + " must be a list");
        List<String> result = new ArrayList<>();
        for (Object entry : collection) {
            String name = string(entry, field);
            if (!name.isEmpty()) result.add(name);
        }
        return result;
    }
    private static Map<?, ?> map(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(field + " must be a YAML mapping");
        return map;
    }
    private static String string(Object value, String field) {
        if (!(value instanceof String string)) throw new IllegalArgumentException(field + " must be text");
        return string;
    }
    private static int coordinate(Map<?, ?> data, String key) {
        if (!data.containsKey(key)) throw new IllegalArgumentException("Missing coordinate " + key);
        return FlagRegistry.integer(data.get(key));
    }

    private static Object safeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) return value;
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, child) -> result.put(string(key, "extension flag key"), safeValue(child)));
            return result;
        }
        if (value instanceof Collection<?> collection) return new ArrayList<>(collection.stream().map(RegionRepository::safeValue).toList());
        throw new IllegalArgumentException("Unsupported YAML flag value: " + value.getClass().getSimpleName());
    }

    private static void write(Path target, Map<String, Region> regions) throws IOException {
        validate(regions);
        Map<String, Object> data = new LinkedHashMap<>();
        regions.values().stream().sorted(java.util.Comparator.comparing(Region::id)).forEach(region -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", region.type());
            if (region.type().equals("cuboid")) {
                entry.put("min", Map.of("x", region.minX(), "y", region.minY(), "z", region.minZ()));
                entry.put("max", Map.of("x", region.maxX(), "y", region.maxY(), "z", region.maxZ()));
            } else if (region.type().equals("poly2d")) {
                entry.put("min-y", region.minY()); entry.put("max-y", region.maxY());
                entry.put("points", region.points().stream().map(point -> Map.of("x", point.x(), "z", point.z())).toList());
            }
            entry.put("priority", region.priority());
            Map<String, Object> flags = new LinkedHashMap<>();
            region.flags().forEach((name, value) -> {
                Object normalized = FlagRegistry.contains(name) ? FlagRegistry.normalize(name, value) : value;
                if (normalized != null) flags.put(name, safeValue(normalized));
            });
            entry.put("flags", flags);
            entry.put("owners", saveDomain(region.owners())); entry.put("members", saveDomain(region.members()));
            if (region.parent() != null) entry.put("parent", region.parent());
            data.put(region.id(), entry);
        });
        String output = "# WorldGuard NeoForge regions; compatible with WorldGuard YAML.\n" + yaml().dump(Map.of("regions", data));
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "regions-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(output);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Map<String, Object> saveDomain(Domain domain) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!domain.uuids().isEmpty()) result.put("unique-ids", domain.uuids().stream().map(UUID::toString).sorted().toList());
        if (!domain.players().isEmpty()) result.put("players", domain.players().stream().sorted().toList());
        if (!domain.groups().isEmpty()) result.put("groups", domain.groups().stream().sorted().toList());
        return result;
    }
}
