package com.sk89q.worldguard.neoforge.region;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Domain conversion, shared by startup migration and explicitly requested migrations. */
public final class ProfileMigration {
    private ProfileMigration() { }
    public static int apply(Collection<Region> regions, Map<String,UUID> profiles, boolean keepUnresolved) {
        return apply(regions,profiles,keepUnresolved,null);
    }
    public static int apply(Collection<Region> regions, Map<String,UUID> profiles, boolean keepUnresolved, java.util.Set<String> requested) {
        int changed=0;
        for (Region region : regions) for (Domain domain : java.util.List.of(region.owners(),region.members())) {
            var iterator=domain.players().iterator();
            while (iterator.hasNext()) {
                String name=iterator.next(); UUID uuid=profiles.get(name.toLowerCase(java.util.Locale.ROOT));
                if(requested!=null && !requested.contains(name.toLowerCase(java.util.Locale.ROOT))) continue;
                if (uuid != null) { domain.uuids().add(uuid); iterator.remove(); changed++; }
                else if (!keepUnresolved) { iterator.remove(); changed++; }
            }
        }
        return changed;
    }
}
