package com.sk89q.worldguard.neoforge.sessions;

import java.util.Collection;
import java.util.Locale;

/** Shared normalization for command input and WorldGuard allowed/blocked patterns. */
public final class CommandPatterns {
    private CommandPatterns() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String command = value.strip();
        if (command.startsWith("/")) {
            command = command.substring(1).stripLeading();
        }
        command = command.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        int separator = command.indexOf(' ');
        String root = separator < 0 ? command : command.substring(0, separator);
        root = root.substring(root.lastIndexOf(':') + 1);
        return root + (separator < 0 ? "" : command.substring(separator));
    }

    public static boolean matches(Collection<String> patterns, String command) {
        String normalizedCommand = normalize(command);
        for (String pattern : patterns) {
            if (matchesNormalized(normalize(pattern), normalizedCommand)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(String pattern, String command) {
        return matchesNormalized(normalize(pattern), normalize(command));
    }

    private static boolean matchesNormalized(String pattern, String command) {
        if (pattern.isEmpty() || command.isEmpty()) {
            return false;
        }
        if (pattern.equals("*") || command.equals(pattern) || command.startsWith(pattern + " ")) {
            return true;
        }
        return pattern.endsWith("*") && command.startsWith(pattern.substring(0, pattern.length() - 1));
    }
}
