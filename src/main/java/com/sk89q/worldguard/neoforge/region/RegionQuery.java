package com.sk89q.worldguard.neoforge.region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** WorldGuard's priority, inheritance and domain rules as a pure query. */
public final class RegionQuery {
    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final UUID playerId;
    private final String playerName;
    private final Set<String> sourceRegionIds;
    private final Set<String> sourceDomains;
    private final java.util.function.Predicate<Region> ownerAssociation;
    private java.util.function.Predicate<String> groupMembership = ignored -> false;
    public RegionQuery withGroups(java.util.function.Predicate<String> membership) {
        groupMembership = java.util.Objects.requireNonNull(membership); return this;
    }

    public RegionQuery(Collection<Region> regions, UUID playerId, String playerName) {
        this(regions, playerId, playerName, null, Set.of());
    }

    /** Automation owns regions shared with its source, including explicit protection domains. */
    public RegionQuery(Collection<Region> regions, UUID playerId, String playerName,
                       Set<String> sourceRegionIds, Set<String> sourceDomains) {
        this(regions, playerId, playerName, sourceRegionIds, sourceDomains, null);
    }

    public RegionQuery(Collection<Region> regions, UUID playerId, String playerName,
                       java.util.function.Predicate<Region> ownerAssociation) {
        this(regions, playerId, playerName, null, Set.of(), ownerAssociation);
    }

    private RegionQuery(Collection<Region> regions, UUID playerId, String playerName,
                        Set<String> sourceRegionIds, Set<String> sourceDomains,
                        java.util.function.Predicate<Region> ownerAssociation) {
        for (Region region : regions) this.regions.put(region.id(), region);
        this.playerId = playerId;
        this.playerName = playerName;
        this.sourceRegionIds = sourceRegionIds == null ? null : Set.copyOf(sourceRegionIds);
        this.sourceDomains = sourceDomains == null ? Set.of() : Set.copyOf(sourceDomains);
        this.ownerAssociation = ownerAssociation;
    }

    public Collection<Region> regions() { return List.copyOf(regions.values()); }

    /** Includes inherited parents even when their physical geometry does not contain the point. */
    public List<Region> applicable(int x, int y, int z) {
        Set<String> included = new HashSet<>();
        List<Region> result = new ArrayList<>();
        for (Region region : regions.values()) {
            if (!region.id().equals("__global__") && !region.contains(x, y, z)) continue;
            for (Region current : ancestry(region)) {
                if (included.add(current.id())) result.add(current);
            }
        }
        result.sort(Comparator.comparingInt(RegionQuery::priority).reversed()
                .thenComparing(Comparator.comparingInt((Region region) -> ancestry(region).size()).reversed())
                .thenComparing(Region::id));
        return result;
    }

    public boolean isOwner(Region region) {
        if (ownerAssociation != null) return ancestry(region).stream().anyMatch(ownerAssociation);
        if (sourceRegionIds != null) {
            for (Region current : ancestry(region)) {
                if (current.id().equals("__global__") && sourceRegionIds.isEmpty() || sourceRegionIds.contains(current.id())) return true;
                for (Region ancestor : ancestry(current)) {
                    Object domains = rawFlag(ancestor, "nonplayer-protection-domains");
                    if (domains instanceof Collection<?> values) {
                        if (values.stream().anyMatch(value -> sourceDomains.contains(value.toString()))) return true;
                        break;
                    }
                }
            }
            return false;
        }
        return ancestry(region).stream().anyMatch(current -> current.owners().contains(playerId, playerName)
                || current.owners().groups().stream().anyMatch(groupMembership));
    }
    public boolean isMember(Region region) {
        return isOwner(region) || ancestry(region).stream().anyMatch(current -> current.members().contains(playerId, playerName)
                || current.members().groups().stream().anyMatch(groupMembership));
    }

    public Object value(int x, int y, int z, String flag) {
        String canonical = canonical(flag);
        List<Object> values = valuesInternal(applicable(x, y, z), canonical, true);
        return combine(canonical, values);
    }

    public Object explicitValue(int x, int y, int z, String flag) {
        String canonical = canonical(flag);
        return combine(canonical, valuesInternal(applicable(x, y, z), canonical, false));
    }

    /** All values at the winning priority, useful for deny-spawn and command sets. */
    public List<Object> values(int x, int y, int z, String flag) {
        return valuesInternal(applicable(x, y, z), canonical(flag), true);
    }

    /** Environmental rules have a permissive unset value. */
    public boolean allows(int x, int y, int z, String flag) {
        Object value = value(x, y, z, flag);
        return !"deny".equals(value) && !Boolean.FALSE.equals(value);
    }

    public boolean canBuild(int x, int y, int z) { return "allow".equals(value(x, y, z, "build")); }

    /** An explicit action ALLOW overrides membership protection, as WorldGuard testBuild does. */
    public boolean canUse(int x, int y, int z, String flag) {
        return canUse(x, y, z, new String[]{flag});
    }

    public boolean canUse(int x, int y, int z, String... flags) {
        boolean allowed = false;
        for (String flag : flags) {
            Object action = value(x, y, z, flag);
            if ("deny".equals(action) || Boolean.FALSE.equals(action)) return false;
            if ("allow".equals(action) || Boolean.TRUE.equals(action)) allowed = true;
        }
        return allowed || canBuild(x, y, z);
    }

    private List<Object> valuesInternal(List<Region> applicable, String flag, boolean includeDefault) {
        int minimumPriority = Integer.MIN_VALUE;
        List<Object> values = new ArrayList<>();
        Set<String> ignored = new HashSet<>();
        for (Region region : applicable) {
            int priority = priority(region);
            if (priority < minimumPriority) break;
            if (ignored.contains(region.id())) continue;
            Object value = effective(region, flag);
            if (value != null) {
                minimumPriority = priority;
                values.add(value);
            }
            ignoreParents(region, ignored);
            if (priority != minimumPriority && flag.equals("build") && !"allow".equals(effective(region, "passthrough"))) {
                minimumPriority = priority;
            }
        }
        if (!values.isEmpty() || !includeDefault) return List.copyOf(values);
        if (flag.equals("build")) {
            Membership membership = membership(applicable);
            if (membership == Membership.FAIL) return List.of();
            if (membership == Membership.SUCCESS) return List.of("allow");
        }
        Object fallback = FlagRegistry.contains(flag) ? FlagRegistry.defaultValue(flag) : null;
        return fallback == null ? List.of() : List.of(fallback);
    }

    private Membership membership(List<Region> applicable) {
        int minimumPriority = Integer.MIN_VALUE;
        Membership result = Membership.NO_REGIONS;
        Set<String> ignored = new HashSet<>();
        for (Region region : applicable) {
            int priority = priority(region);
            if (priority < minimumPriority) break;
            if (ignored.contains(region.id()) || "allow".equals(effective(region, "passthrough"))) continue;
            minimumPriority = priority;
            if (!isMember(region)) return Membership.FAIL;
            result = Membership.SUCCESS;
            ignoreParents(region, ignored);
        }
        return result;
    }

    private Object effective(Region region, String flag) {
        if (region.id().equals("__global__")) {
            if (flag.equals("passthrough")) {
                Object raw = rawFlag(region, flag);
                return !"deny".equals(raw) && ("allow".equals(raw) || region.owners().isEmpty() && region.members().isEmpty()) ? "allow" : null;
            }
            if (flag.equals("build")) {
                Object raw = rawFlag(region, flag);
                return "allow".equals(raw) ? null : raw;
            }
        }
        boolean owner = isOwner(region), member = isMember(region);
        for (Region current : ancestry(region)) {
            Object value = rawFlag(current, flag);
            if (value == null) continue;
            String group = FlagRegistry.contains(flag) ? FlagRegistry.defaultGroup(flag) : "all";
            if (group != null) {
                Object configured = rawFlag(current, flag + "-group");
                if (configured != null) group = configured.toString().toLowerCase(java.util.Locale.ROOT).replace("-", "").replace("_", "");
                if (!groupApplies(group, owner, member)) continue;
            }
            return value;
        }
        return null;
    }

    private static boolean groupApplies(String group, boolean owner, boolean member) {
        return switch (group) {
            case "all" -> true;
            case "owners" -> owner;
            case "members" -> member;
            case "nonowners" -> !owner;
            case "nonmembers" -> !member;
            default -> false;
        };
    }

    private static Object rawFlag(Region region, String name) {
        Object raw = region.flags().get(name);
        if (raw == null) return null;
        return FlagRegistry.contains(name) ? FlagRegistry.normalize(name, raw) : raw;
    }

    private List<Region> ancestry(Region region) {
        List<Region> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Region current = region; current != null; current = current.parent() == null ? null : regions.get(current.parent())) {
            if (!seen.add(current.id())) throw new IllegalStateException("Region parent cycle: " + current.id());
            result.add(current);
            if (current.parent() != null && !regions.containsKey(current.parent())) {
                throw new IllegalStateException("Missing region parent: " + current.parent());
            }
        }
        return result;
    }
    private void ignoreParents(Region region, Set<String> ignored) {
        List<Region> ancestry = ancestry(region);
        for (int i = 1; i < ancestry.size(); i++) ignored.add(ancestry.get(i).id());
    }

    private static int priority(Region region) { return region.id().equals("__global__") ? Integer.MIN_VALUE : region.priority(); }
    private static String canonical(String flag) {
        String canonical = FlagRegistry.canonicalName(flag);
        return canonical == null ? flag : canonical;
    }
    private static Object combine(String flag, List<Object> values) {
        if (FlagRegistry.isState(flag)) {
            if (values.contains("deny")) return "deny";
            return values.contains("allow") ? "allow" : null;
        }
        return values.isEmpty() ? null : values.getFirst();
    }
    private enum Membership { NO_REGIONS, FAIL, SUCCESS }
}
