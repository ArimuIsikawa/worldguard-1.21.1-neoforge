package com.sk89q.worldguard.neoforge.region;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Built-in WorldGuard 7 flag types, without any Bukkit/WorldEdit classes. */
public final class FlagRegistry {
    public enum Type { STATE, BOOLEAN, INTEGER, DOUBLE, STRING, SET, LOCATION, GAME_MODE, WEATHER, GROUP }
    public record Definition(Type type, Object defaultValue, String defaultGroup) { }
    private static final Map<String, Definition> FLAGS = new LinkedHashMap<>();
    private static final Set<String> GROUPS = Set.of("members", "owners", "nonmembers", "nonowners", "all", "none");

    static {
        states(null, "passthrough block-break block-place use interact damage-animals pvp sleep respawn-anchors tnt chest-access vehicle-place vehicle-destroy lighter ride potion-splash item-frame-rotation block-trampling firework-damage use-anvil use-dripleaf wind-charge-burst frosted-ice-form invincible");
        states("allow", "build item-pickup item-drop exp-drops mob-damage creeper-explosion enderdragon-block-damage ghast-fireball other-explosion breeze-charge-explosion wither-damage enderman-grief snowman-trails ravager-grief entity-painting-destroy entity-item-frame-destroy mob-spawning pistons fire-spread lava-fire lightning snow-fall snow-melt ice-form ice-melt frosted-ice-melt mushroom-growth leaf-decay grass-growth mycelium-spread vine-growth rock-growth sculk-growth crop-growth soil-dry coral-fade copper-fade water-flow lava-flow moisture-change lava-harden send-chat receive-chat fall-damage natural-health-regen natural-hunger-drain exit-via-teleport enderpearl chorus-fruit-teleport");
        define("entry", Type.STATE, "allow", "nonmembers");
        define("exit", Type.STATE, "allow", "nonmembers");
        define("nonplayer-protection-domains", Type.SET, null, null);
        define("deny-spawn", Type.SET, null, "all");
        define("blocked-cmds", Type.SET, null, "all");
        define("allowed-cmds", Type.SET, null, "all");
        define("teleport", Type.LOCATION, null, "all");
        define("spawn", Type.LOCATION, null, "members");
        define("weather-lock", Type.WEATHER, null, "all");
        define("game-mode", Type.GAME_MODE, null, "all");
        for (String name : "time-lock greeting farewell greeting-title farewell-title".split(" ")) define(name, Type.STRING, null, "all");
        define("teleport-message", Type.STRING, "§dTeleported you to the region '%id%'.", "all");
        define("deny-message", Type.STRING, "§c§lHey! §7Sorry, but you can't %what% here.", "all");
        define("entry-deny-message", Type.STRING, "§c§lHey! §7You are not permitted to enter this area.", "all");
        define("exit-deny-message", Type.STRING, "§c§lHey! §7You are not permitted to leave this area.", "all");
        for (String name : "exit-override notify-enter notify-leave".split(" ")) define(name, Type.BOOLEAN, null, "all");
        for (String name : "heal-delay heal-amount feed-delay feed-amount feed-min-hunger feed-max-hunger".split(" ")) define(name, Type.INTEGER, null, "all");
        for (String name : "heal-min-health heal-max-health".split(" ")) define(name, Type.DOUBLE, null, "all");
        Map<String, Definition> groups = new LinkedHashMap<>();
        FLAGS.forEach((name, definition) -> {
            if (definition.defaultGroup != null) groups.put(name + "-group", new Definition(Type.GROUP, definition.defaultGroup, null));
        });
        FLAGS.putAll(groups);
    }

    private FlagRegistry() { }
    private static void states(Object defaultValue, String names) {
        for (String name : names.split(" ")) define(name, Type.STATE, defaultValue, "all");
    }
    private static void define(String name, Type type, Object defaultValue, String group) {
        FLAGS.put(name, new Definition(type, defaultValue, group));
    }

    public static synchronized Set<String> names() { return Collections.unmodifiableSet(new LinkedHashSet<>(FLAGS.keySet())); }
    /** Native extension registration. Conflicting definitions fail before a server loads regions. */
    public static synchronized void register(String name, Type type, Object defaultValue, String group) {
        if (name == null || !name.matches("[a-z][a-z0-9:-]{0,63}") || name.endsWith("-group")) throw new IllegalArgumentException("Invalid extension flag name");
        if (group != null && !GROUPS.contains(group)) throw new IllegalArgumentException("Invalid extension flag group");
        Definition previous = FLAGS.get(name);
        Definition requested = new Definition(java.util.Objects.requireNonNull(type), defaultValue, group);
        if (previous != null) {
            if (previous.type != type || !previous.equals(new Definition(type,normalize(name,defaultValue),group))) throw new IllegalArgumentException("Flag definition conflict: " + name);
            return;
        }
        FLAGS.put(name, requested);
        try {
            FLAGS.put(name, new Definition(type, normalize(name, defaultValue), group));
            if (group != null) FLAGS.put(name + "-group", new Definition(Type.GROUP, group, null));
        } catch (RuntimeException e) { FLAGS.remove(name); throw e; }
    }
    public static boolean contains(String name) { return canonicalName(name) != null; }
    /** WorldGuard accepts spelling differing only by hyphens and case. */
    public static synchronized String canonicalName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        if (FLAGS.containsKey(lower)) return lower;
        String compact = lower.replace("-", "");
        return FLAGS.keySet().stream().filter(key -> key.replace("-", "").equals(compact)).findFirst().orElse(null);
    }
    public static synchronized Definition definition(String name) {
        String canonical = canonicalName(name);
        if (canonical == null) throw new IllegalArgumentException("Unknown flag: " + name);
        return FLAGS.get(canonical);
    }
    public static Type type(String name) { return definition(name).type; }
    public static boolean isState(String name) { return contains(name) && type(name) == Type.STATE; }
    public static Object defaultValue(String name) { return definition(name).defaultValue; }
    public static String defaultGroup(String name) { return definition(name).defaultGroup; }

    public static Object parse(String name, String input) {
        String canonical = canonicalName(name);
        Definition definition = definition(name);
        if (input == null) return null;
        if (definition.type == Type.SET) {
            return normalize(canonical, input.isBlank() ? Set.of() : Arrays.asList(input.split(",")));
        }
        if (definition.type == Type.LOCATION) {
            String[] parts = input.split(",", -1);
            if (parts.length < 3 || parts.length > 6) throw new IllegalArgumentException("Expected x,y,z[,yaw,pitch,world]");
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("world", parts.length == 6 ? parts[5].trim() : "");
            location.put("x", finiteNumber(parts[0])); location.put("y", finiteNumber(parts[1])); location.put("z", finiteNumber(parts[2]));
            location.put("yaw", parts.length >= 4 ? finiteNumber(parts[3]) : 0d);
            location.put("pitch", parts.length >= 5 ? finiteNumber(parts[4]) : 0d);
            return location;
        }
        return normalize(canonical, input);
    }

    /** Validate and normalize a YAML value. Unknown extension flags are retained by the repository. */
    public static Object normalize(String name, Object value) {
        if (value == null) return null;
        String canonical = canonicalName(name);
        Definition definition = definition(name);
        String text = value.toString().trim();
        return switch (definition.type) {
            case STATE -> {
                String state = text.toLowerCase(Locale.ROOT);
                if (state.equals("none")) yield null;
                if (!state.equals("allow") && !state.equals("deny")) throw invalid(name, "allow/deny/none", value);
                yield state;
            }
            case BOOLEAN -> {
                if (value instanceof Boolean) yield value;
                if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) yield Boolean.parseBoolean(text);
                throw invalid(name, "true/false", value);
            }
            case INTEGER -> integer(value);
            case DOUBLE -> finiteNumber(value);
            case STRING -> {
                if (value instanceof Collection<?> || value instanceof Map<?, ?>) throw invalid(name, "text", value);
                yield value.toString();
            }
            case GROUP -> {
                String group = text.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
                if (!GROUPS.contains(group)) throw invalid(name, String.join("/", GROUPS), value);
                yield group;
            }
            case GAME_MODE -> {
                String mode = text.toLowerCase(Locale.ROOT).replace("minecraft:", "");
                if (!Set.of("survival", "creative", "adventure", "spectator").contains(mode)) throw invalid(name, "survival/creative/adventure/spectator", value);
                yield mode;
            }
            case WEATHER -> {
                String weather = text.toLowerCase(Locale.ROOT).replace("minecraft:", "");
                if (!Set.of("clear", "rain", "thunder").contains(weather)) throw invalid(name, "clear/rain/thunder", value);
                yield weather;
            }
            case SET -> {
                if (!(value instanceof Collection<?> collection)) throw invalid(name, "a YAML list", value);
                Set<String> values = new LinkedHashSet<>();
                for (Object item : collection) {
                    if (item == null || item instanceof Collection<?> || item instanceof Map<?, ?>) throw invalid(name, "a list of text values", value);
                    String entry = item.toString().trim();
                    if (entry.isEmpty()) continue;
                    if (canonical.equals("deny-spawn")) {
                        entry = entry.toLowerCase(Locale.ROOT);
                        if (!entry.contains(":")) entry = "minecraft:" + entry;
                        if (!entry.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw invalid(name, "an entity ID", entry);
                    } else if (canonical.equals("blocked-cmds") || canonical.equals("allowed-cmds")) {
                        entry = entry.toLowerCase(Locale.ROOT);
                        if (!entry.startsWith("/")) entry = "/" + entry;
                    }
                    values.add(entry);
                }
                yield values;
            }
            case LOCATION -> {
                if (!(value instanceof Map<?, ?> raw)) throw invalid(name, "a location map", value);
                if (!raw.containsKey("world")) throw invalid(name, "a world,x,y,z,yaw,pitch location", value);
                Map<String, Object> location = new LinkedHashMap<>();
                location.put("world", String.valueOf(raw.get("world")));
                for (String coordinate : new String[]{"x", "y", "z"}) {
                    if (!raw.containsKey(coordinate)) throw invalid(name, "a location with " + coordinate, value);
                    location.put(coordinate, finiteNumber(raw.get(coordinate)));
                }
                location.put("yaw", finiteNumber(raw.containsKey("yaw") ? raw.get("yaw") : 0));
                location.put("pitch", finiteNumber(raw.containsKey("pitch") ? raw.get("pitch") : 0));
                yield location;
            }
        };
    }

    static int integer(Object value) {
        try {
            if (value instanceof Number number) {
                double decimal = number.doubleValue();
                if (!Double.isFinite(decimal) || decimal != number.intValue()) throw new NumberFormatException();
                return number.intValue();
            }
            return Integer.parseInt(value.toString().trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Expected a 32-bit integer: " + value, exception);
        }
    }

    private static double finiteNumber(Object value) {
        try {
            double number = value instanceof Number numeric ? numeric.doubleValue() : Double.parseDouble(value.toString().trim());
            if (!Double.isFinite(number)) throw new NumberFormatException();
            return number;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Expected a finite number: " + value, exception);
        }
    }
    private static IllegalArgumentException invalid(String name, String expected, Object value) {
        return new IllegalArgumentException("Flag " + name + " expects " + expected + ": " + value);
    }
}
