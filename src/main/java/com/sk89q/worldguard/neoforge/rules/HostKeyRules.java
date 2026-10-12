package com.sk89q.worldguard.neoforge.rules;

import java.util.Map;
import java.util.UUID;

/** Original per-player hostname checks, independent of networking and Minecraft. */
public final class HostKeyRules {
    private HostKeyRules() { }
    public static String expected(Object configured, UUID uuid, String name) {
        if (!(configured instanceof Map<?, ?> values)) return null;
        String byName = null;
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) continue;
            String key = entry.getKey().toString();
            if (uuid != null && key.equalsIgnoreCase(uuid.toString())) return entry.getValue().toString();
            if (name != null && key.equalsIgnoreCase(name)) byName = entry.getValue().toString();
        }
        return byName;
    }
    public static boolean matches(String expected, String hostname, boolean allowForgeSuffix) {
        if (expected == null) return true;
        if (hostname == null) return false;
        if (expected.equals(hostname)) return true;
        int colon = hostname.indexOf(':');
        String host = colon < 0 ? hostname : hostname.substring(0, colon);
        if (expected.equals(host)) return true;
        return allowForgeSuffix && (host.equals(expected + "\u0000FML\u0000")
                || host.equals(expected + "\u0000FML2\u0000") || host.equals(expected + "\u0000FML3\u0000"));
    }
}
