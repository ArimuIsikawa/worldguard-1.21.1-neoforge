package com.sk89q.worldguard.neoforge.region;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileMigrationTest {
    @Test void profilesReplaceNamesCaseInsensitivelyAndRetainUnresolvedOwnersByDefault() {
        Region region=Region.cuboid("home",0,0,0,1,1,1); region.owners().players().addAll(Set.of("ALICE","Unknown")); region.members().players().add("Alice");
        UUID uuid=UUID.randomUUID(); assertEquals(2,ProfileMigration.apply(List.of(region),Map.of("alice",uuid),true));
        assertEquals(Set.of(uuid),region.owners().uuids()); assertEquals(Set.of(uuid),region.members().uuids()); assertEquals(Set.of("Unknown"),region.owners().players());
    }
    @Test void unresolvedNamesAreRemovedOnlyWithTheExplicitOptionAndExistingUuidsSurvive() {
        Region region=Region.global("template"); UUID existing=UUID.randomUUID(); region.owners().uuids().add(existing); region.owners().players().add("Unknown");
        assertEquals(1,ProfileMigration.apply(List.of(region),Map.of(),false)); assertTrue(region.owners().players().isEmpty()); assertEquals(Set.of(existing),region.owners().uuids());
    }
    @Test void namesAddedDuringAnAsyncLookupAreNotDeletedByThatMigration() {
        Region region=Region.global("template"); region.owners().players().addAll(Set.of("Requested","NewOwner"));
        assertEquals(1,ProfileMigration.apply(List.of(region),Map.of(),false,Set.of("requested")));
        assertEquals(Set.of("NewOwner"),region.owners().players());
    }
}
