package com.sk89q.worldguard.neoforge.region;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A WorldGuard domain. Legacy permission group names are retained for round trips. */
public final class Domain {
    private final Set<UUID> uuids = new LinkedHashSet<>();
    private final Set<String> players = new LinkedHashSet<>();
    private final Set<String> groups = new LinkedHashSet<>();

    public Set<UUID> uuids() { return uuids; }
    public Set<String> players() { return players; }
    public Set<String> groups() { return groups; }

    public boolean contains(UUID uuid, String playerName) {
        return uuid != null && uuids.contains(uuid)
                || playerName != null && players.stream().anyMatch(playerName::equalsIgnoreCase);
    }

    public boolean isEmpty() { return uuids.isEmpty() && players.isEmpty() && groups.isEmpty(); }

    public void clear() { uuids.clear(); players.clear(); groups.clear(); }

    public Domain copy() {
        Domain result = new Domain();
        result.uuids.addAll(uuids);
        result.players.addAll(players);
        result.groups.addAll(groups);
        return result;
    }
}
