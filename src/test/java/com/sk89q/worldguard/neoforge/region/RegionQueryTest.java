package com.sk89q.worldguard.neoforge.region;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RegionQueryTest {
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static Region area(String id) { return Region.cuboid(id, 0, 0, 0, 10, 10, 10); }
    private static RegionQuery query(Region... regions) { return new RegionQuery(List.of(regions), MEMBER, "Alice"); }

    @Test void wildernessPermitsBuildAndEnvironment() {
        RegionQuery query = query();
        assertTrue(query.canBuild(5, 5, 5));
        assertTrue(query.canUse(5, 5, 5, "block-break"));
        assertTrue(query.allows(5, 5, 5, "pvp"));
        assertNull(query.value(5, 5, 5, "pvp"));
    }

    @Test void regionImplicitlyProtectsAgainstNonmembers() {
        Region region = area("plot");
        assertFalse(query(region).canBuild(5, 5, 5));
        assertFalse(query(region).canUse(5, 5, 5, "block-break"));
        assertTrue(query(region).canBuild(11, 5, 5));
        region.members().uuids().add(MEMBER);
        assertTrue(query(region).canBuild(5, 5, 5));
    }

    @Test void ownerCountsAsMemberAndLegacyNamesIgnoreCase() {
        Region region = area("plot");
        region.owners().players().add("aLiCe");
        assertTrue(query(region).isOwner(region));
        assertTrue(query(region).isMember(region));
        assertTrue(query(region).canBuild(5, 5, 5));
        assertFalse(new RegionQuery(List.of(region), STRANGER, "Bob").canBuild(5, 5, 5));
    }

    @Test void permissionGroupsArePreservedButDoNotGrantMembership() {
        Region region = area("plot");
        region.members().groups().add("admin");
        assertFalse(query(region).canBuild(5, 5, 5));
    }

    @Test void actionAllowOverridesMembershipAndActionDenyBlocksOwner() {
        Region region = area("plot");
        region.flags().put("block-break", "allow");
        assertTrue(query(region).canUse(5, 5, 5, "block-break"));
        region.owners().uuids().add(MEMBER);
        region.flags().put("block-break", "deny");
        assertFalse(query(region).canUse(5, 5, 5, "block-break"));
        assertTrue(query(region).canBuild(5, 5, 5));
    }

    @Test void explicitBuildDenyStillAllowsExplicitActionAllow() {
        Region region = area("plot");
        region.flags().put("build", "deny");
        region.flags().put("block-break", "allow");
        assertFalse(query(region).canBuild(5, 5, 5));
        assertTrue(query(region).canUse(5, 5, 5, "block-break"));
    }

    @Test void higherPriorityWinsAndEqualPriorityDenyWins() {
        Region deny = area("a-deny"), allow = area("b-allow");
        deny.flags().put("pvp", "deny"); allow.flags().put("pvp", "allow");
        assertFalse(query(deny, allow).allows(5, 5, 5, "pvp"));
        allow.priority(5);
        assertTrue(query(deny, allow).allows(5, 5, 5, "pvp"));
        assertEquals("allow", query(allow, deny).value(5, 5, 5, "pvp"));
    }

    @Test void flagAbsentOnHighPriorityFallsBackToLowerPriority() {
        Region high = area("high"), low = area("low"); high.priority(50);
        low.flags().put("pvp", "deny");
        assertFalse(query(high, low).allows(5, 5, 5, "pvp"));
    }

    @Test void implicitBuildOnHighPriorityDoesNotFallBackToLowBuildAllow() {
        Region high = area("high"), low = area("low"); high.priority(50);
        low.flags().put("build", "allow");
        assertFalse(query(high, low).canBuild(5, 5, 5));
        high.flags().put("passthrough", "allow");
        assertTrue(query(high, low).canBuild(5, 5, 5));
    }

    @Test void membershipMustCoverEveryEqualPriorityOverlap() {
        Region mine = area("mine"), other = area("other");
        mine.members().uuids().add(MEMBER);
        assertFalse(query(mine, other).canBuild(5, 5, 5));
        mine.priority(1);
        assertTrue(query(mine, other).canBuild(5, 5, 5));
    }

    @Test void parentFlagsAndDomainsAreInheritedOutsideParentGeometry() {
        Region parent = Region.cuboid("parent", 100, 0, 100, 110, 10, 110), child = area("child");
        child.parent("parent");
        parent.owners().uuids().add(MEMBER);
        parent.flags().put("pvp", "deny");
        assertTrue(query(parent, child).canBuild(5, 5, 5));
        assertFalse(query(parent, child).allows(5, 5, 5, "pvp"));
        assertEquals(2, query(parent, child).applicable(5, 5, 5).size());
    }

    @Test void childOverridesParentAtSamePriority() {
        Region parent = area("a-parent"), child = area("z-child"); child.parent("a-parent");
        parent.flags().put("pvp", "deny"); child.flags().put("pvp", "allow");
        assertTrue(query(parent, child).allows(5, 5, 5, "pvp"));
    }

    @Test void parentWithHigherPriorityOverridesChild() {
        Region parent = area("parent"), child = area("child"); child.parent("parent");
        parent.priority(1); parent.flags().put("pvp", "deny"); child.flags().put("pvp", "allow");
        assertFalse(query(parent, child).allows(5, 5, 5, "pvp"));
    }

    @Test void globalIsFallbackRegardlessOfStoredPriority() {
        Region global = Region.global("__global__"), local = area("local");
        global.priority(Integer.MAX_VALUE); global.flags().put("pvp", "deny"); local.flags().put("pvp", "allow");
        assertTrue(query(global, local).allows(5, 5, 5, "pvp"));
        assertFalse(query(global, local).allows(50, 5, 50, "pvp"));
        assertTrue(query(global).canBuild(5, 5, 5));
    }

    @Test void globalBuildAllowCannotOverrideMembershipProtection() {
        Region global = Region.global("__global__"), local = area("local");
        global.flags().put("build", "allow");
        assertFalse(query(global, local).canBuild(5, 5, 5));
        assertTrue(query(global).canBuild(5, 5, 5));
    }

    @Test void globalDomainOrPassthroughDenyProtectsWilderness() {
        Region global = Region.global("__global__");
        global.members().uuids().add(STRANGER);
        assertFalse(query(global).canBuild(50, 5, 50));
        global.members().clear(); global.flags().put("passthrough", "deny");
        assertFalse(query(global).canBuild(50, 5, 50));
    }

    @Test void globalDenyDoesNotOverrideHigherPriorityMemberBuild() {
        Region global = Region.global("__global__"), local = area("local");
        global.flags().put("build", "deny"); local.members().uuids().add(MEMBER);
        assertTrue(query(global, local).canBuild(5, 5, 5));
        assertFalse(query(global, local).canBuild(50, 5, 50));
    }

    @Test void passthroughSkipsOnlyMembershipAndRetainsExplicitFlags() {
        Region region = area("plot"); region.flags().put("passthrough", "allow"); region.flags().put("pvp", "deny");
        assertTrue(query(region).canBuild(5, 5, 5));
        assertFalse(query(region).allows(5, 5, 5, "pvp"));
    }

    @Test void entryAndExitDefaultToNonmembers() {
        Region region = area("plot"); region.members().uuids().add(MEMBER); region.flags().put("entry", "deny");
        assertTrue(query(region).allows(5, 5, 5, "entry"));
        assertFalse(new RegionQuery(List.of(region), STRANGER, "Bob").allows(5, 5, 5, "entry"));
        region.flags().put("entry-group", "all");
        assertFalse(query(region).allows(5, 5, 5, "entry"));
    }

    @Test void perFlagGroupsIncludeOwnersAndRespectNone() {
        Region region = area("plot"); region.members().uuids().add(MEMBER); region.flags().put("pvp", "deny"); region.flags().put("pvp-group", "owners");
        assertTrue(query(region).allows(5, 5, 5, "pvp"));
        region.owners().uuids().add(MEMBER);
        assertFalse(query(region).allows(5, 5, 5, "pvp"));
        region.flags().put("pvp-group", "none");
        assertTrue(query(region).allows(5, 5, 5, "pvp"));
    }

    @Test void inapplicableChildFlagCanInheritApplicableParentFlag() {
        Region parent = area("parent"), child = area("child"); child.parent("parent");
        child.flags().put("pvp", "allow"); child.flags().put("pvp-group", "members"); parent.flags().put("pvp", "deny");
        assertFalse(query(parent, child).allows(5, 5, 5, "pvp"));
    }

    @Test void allSetValuesAtEqualPriorityAreAvailable() {
        Region one = area("one"), two = area("two");
        one.flags().put("deny-spawn", List.of("zombie")); two.flags().put("deny-spawn", List.of("skeleton"));
        assertEquals(2, query(one, two).values(5, 5, 5, "deny-spawn").size());
        two.priority(1);
        assertEquals(1, query(one, two).values(5, 5, 5, "deny-spawn").size());
    }

    @Test void malformedParentLinksFailInsteadOfPermitting() {
        Region region = area("plot"); region.parent("missing");
        assertThrows(IllegalStateException.class, () -> query(region).canBuild(5, 5, 5));
        Region parent = area("parent"); parent.parent("plot"); region.parent("parent");
        assertThrows(IllegalStateException.class, () -> query(region, parent).canBuild(5, 5, 5));
    }

    @Test void automationOwnsSourceRegionsAndSharedProtectionDomains() {
        Region target = area("target");
        RegionQuery same = new RegionQuery(List.of(target), null, null, Set.of("target"), Set.of());
        assertTrue(same.canBuild(5, 5, 5));
        assertFalse(new RegionQuery(List.of(target), null, null, Set.of("other"), Set.of()).canBuild(5, 5, 5));
        target.flags().put("nonplayer-protection-domains", Set.of("shared"));
        assertTrue(new RegionQuery(List.of(target), null, null, Set.of("other"), Set.of("shared")).canBuild(5, 5, 5));
        target.flags().put("build", "deny");
        assertFalse(new RegionQuery(List.of(target), null, null, Set.of("other"), Set.of("shared")).canBuild(5, 5, 5));
    }

    @Test void automationInheritsParentOwnershipAndWildernessOwnsGlobal() {
        Region child = area("child"), parent = area("parent"); child.parent("parent");
        assertTrue(new RegionQuery(List.of(child, parent), null, null, Set.of("parent"), Set.of()).isOwner(child));
        Region global = Region.global("__global__"); global.flags().put("passthrough", "deny");
        assertTrue(new RegionQuery(List.of(global), null, null, Set.of(), Set.of()).canBuild(50, 5, 50));
    }

    @Test void customAutomationAssociationInheritsParentsButNeverOverridesExplicitDeny() {
        Region child = area("child"), parent = area("parent"); child.parent("parent");
        RegionQuery query = new RegionQuery(List.of(child, parent), null, null, region -> region.id().equals("parent"));
        assertTrue(query.isOwner(child)); assertTrue(query.canBuild(5, 5, 5));
        child.flags().put("build", "deny"); assertFalse(query.canBuild(5, 5, 5));
        child.flags().put("block-break", "deny"); assertFalse(query.canUse(5, 5, 5, "block-break"));
        assertFalse(new RegionQuery(List.of(child, parent), null, null, region -> false).isOwner(child));
    }

    @Test void multipleActionFlagsCombineAllowAndDenyBeforeMembership() {
        Region region = area("plot");
        RegionQuery query = query(region);
        assertFalse(query.canUse(5, 5, 5, "use", "chest-access"));
        region.flags().put("chest-access", "allow");
        assertTrue(query.canUse(5, 5, 5, "use", "chest-access"));
        region.flags().put("use", "deny");
        assertFalse(query.canUse(5, 5, 5, "use", "chest-access"));
        region.members().uuids().add(MEMBER);
        assertFalse(query.canUse(5, 5, 5, "use", "chest-access"));
        region.flags().clear();
        assertTrue(query.canUse(5, 5, 5, "use", "chest-access"));
    }
}
