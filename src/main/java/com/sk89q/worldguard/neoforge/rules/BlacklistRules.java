package com.sk89q.worldguard.neoforge.rules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Parser and decision rules for WorldGuard's original blacklist.txt format. */
public final class BlacklistRules {
    public static final Set<String> EVENTS = Set.of("on-break", "on-destroy-with", "on-place", "on-use", "on-interact",
            "on-drop", "on-acquire", "on-equip", "on-dispense");
    private static final Set<String> ACTIONS = Set.of("deny", "allow", "tell", "log", "notify", "kick", "ban");
    public record Rule(Set<String> targets, Map<String, List<String>> actions, String message, String comment,
                       Set<String> ignoreGroups, Set<String> ignorePermissions) {
        public Rule {
            targets = Set.copyOf(targets);
            Map<String, List<String>> copied = new LinkedHashMap<>();
            actions.forEach((event, values) -> copied.put(event, List.copyOf(values)));
            actions = Collections.unmodifiableMap(copied);
            ignoreGroups = Set.copyOf(ignoreGroups); ignorePermissions = Set.copyOf(ignorePermissions);
        }
        public List<String> actions(String event) { return actions.getOrDefault(event, List.of()); }
        public boolean allows(String event, boolean whitelist) {
            boolean allowed = !whitelist;
            for (String action : actions(event)) {
                if (action.equals("allow")) allowed = true;
                else if (action.equals("deny")) allowed = false;
            }
            return allowed;
        }
    }
    private final List<Rule> rules;
    private BlacklistRules(List<Rule> rules) { this.rules = List.copyOf(rules); }
    public static BlacklistRules empty() { return new BlacklistRules(List.of()); }
    public List<Rule> rules() { return rules; }
    public List<Rule> matching(String item) {
        String id = normalizeId(item);
        return rules.stream().filter(rule -> rule.targets.contains(id)).toList();
    }
    public boolean allows(String item, String event, boolean whitelist) {
        // Original WorldGuard whitelist rules affect listed items; unmatched items pass.
        return matching(item).stream().allMatch(rule -> rule.allows(event, whitelist));
    }
    public static BlacklistRules load(Path path) throws IOException {
        return Files.exists(path) ? parse(Files.readString(path)) : empty();
    }
    public static BlacklistRules parse(String contents) throws IOException {
        List<Rule> rules = new ArrayList<>();
        Builder current = null;
        String[] lines = contents.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
            try {
                if (line.startsWith("[")) {
                    if (!line.endsWith("]")) throw new IllegalArgumentException("Unclosed blacklist heading");
                    if (current != null) rules.add(current.build());
                    current = new Builder();
                    for (String id : line.substring(1, line.length() - 1).split(",", -1)) current.targets.add(normalizeId(id));
                } else {
                    if (current == null) throw new IllegalArgumentException("Option before any item heading");
                    int separator = line.indexOf('=');
                    if (separator < 1) throw new IllegalArgumentException("Expected option=value");
                    String option = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                    String value = line.substring(separator + 1).trim();
                    if (EVENTS.contains(option)) {
                        List<String> actions = current.actions.computeIfAbsent(option, ignored -> new ArrayList<>());
                        for (String raw : value.split(",", -1)) {
                            String action = raw.trim().toLowerCase(Locale.ROOT);
                            if (!ACTIONS.contains(action)) throw new IllegalArgumentException("Unknown blacklist action: " + action);
                            actions.add(action);
                        }
                    } else switch (option) {
                        case "message" -> current.message = value;
                        case "comment" -> current.comment = value;
                        case "ignore-groups" -> current.ignoreGroups.addAll(list(value));
                        case "ignore-perms" -> current.ignorePermissions.addAll(list(value));
                        default -> throw new IllegalArgumentException("Unknown blacklist option: " + option);
                    }
                }
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid blacklist at line " + (index + 1) + ": " + exception.getMessage(), exception);
            }
        }
        if (current != null) rules.add(current.build());
        return new BlacklistRules(rules);
    }
    private static Set<String> list(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String name : value.split(",")) if (!name.isBlank()) result.add(name.trim());
        return result;
    }
    public static String normalizeId(String input) {
        String id = input.trim().toLowerCase(Locale.ROOT);
        if (!id.contains(":")) id = "minecraft:" + id;
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid block/item ID: " + input);
        return id;
    }
    private static final class Builder {
        private final Set<String> targets = new LinkedHashSet<>();
        private final Map<String, List<String>> actions = new LinkedHashMap<>();
        private final Set<String> ignoreGroups = new LinkedHashSet<>(), ignorePermissions = new LinkedHashSet<>();
        private String message, comment;
        private Rule build() { return new Rule(targets, actions, message, comment, ignoreGroups, ignorePermissions); }
    }
}
