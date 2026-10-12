package com.sk89q.worldguard.neoforge.rules;

import java.util.UUID;
import java.util.regex.Pattern;

/** Pure owner-line parsing shared by the server sign rules and unit tests. */
public final class SignOwners {
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9A-FK-OR]");
    private SignOwners() { }

    public static boolean allows(String[] lines, UUID uuid, String name) {
        if (!isLock(lines)) return true;
        for (int i = 1; i < Math.min(lines.length, 4); i++) if (matchesOwner(lines[i], uuid, name)) return true;
        return false;
    }
    public static boolean isLock(String[] lines) { return lines.length > 0 && clean(lines[0]).equalsIgnoreCase("[Lock]"); }
    public static boolean matchesOwner(String owner, UUID uuid, String name) {
        String value = clean(owner);
        if (value.regionMatches(true, 0, "name:", 0, 5)) value = value.substring(5);
        else if (value.regionMatches(true, 0, "uuid:", 0, 5)) value = value.substring(5);
        return name != null && !value.isBlank() && value.equalsIgnoreCase(name) || uuid != null && value.equalsIgnoreCase(uuid.toString());
    }
    private static String clean(String line) { return FORMATTING.matcher(line == null ? "" : line).replaceAll("").trim(); }
}
