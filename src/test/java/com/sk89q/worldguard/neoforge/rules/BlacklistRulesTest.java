package com.sk89q.worldguard.neoforge.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BlacklistRulesTest {
    @Test void legacyHeadingsMatchBothVanillaAndModIds() throws IOException {
        BlacklistRules rules = BlacklistRules.parse("""
                # legacy ore section
                [coal_ore, minecraft:gold_ore, mod:rare_ore]
                on-break=deny,log,kick
                on-place=deny,tell
                """);
        assertFalse(rules.allows("minecraft:coal_ore", "on-break", false));
        assertFalse(rules.allows("mod:rare_ore", "on-place", false));
        assertTrue(rules.allows("minecraft:stone", "on-break", false));
        assertTrue(rules.allows("minecraft:coal_ore", "on-drop", false));
        assertEquals(List.of("deny", "log", "kick"), rules.matching("coal_ore").getFirst().actions("on-break"));
    }

    @Test void whitelistFollowsOriginalListedItemSemantics() throws IOException {
        BlacklistRules rules = BlacklistRules.parse("""
                [lava_bucket]
                on-use=allow
                """);
        assertTrue(rules.allows("lava_bucket", "on-use", true));
        assertFalse(rules.allows("lava_bucket", "on-drop", true));
        assertTrue(rules.allows("water_bucket", "on-drop", true));
    }

    @Test void multipleMatchingSectionsRequireEverySectionToAllow() throws IOException {
        BlacklistRules rules = BlacklistRules.parse("""
                [stone]
                on-break=allow
                [stone]
                on-break=deny
                """);
        assertFalse(rules.allows("stone", "on-break", false));
        assertEquals(2, rules.matching("stone").size());
    }

    @Test void actionOrderAndRepeatedOptionsArePreserved() throws IOException {
        BlacklistRules rules = BlacklistRules.parse("""
                [stone]
                on-break=deny
                on-break=allow,log
                """);
        assertTrue(rules.allows("stone", "on-break", false));
        assertEquals(List.of("deny", "allow", "log"), rules.matching("stone").getFirst().actions("on-break"));
    }

    @Test void messagesCommentsAndIgnoredPermissionDataSurviveParsing() throws IOException {
        BlacklistRules rules = BlacklistRules.parse("""
                ; semicolon comments are accepted
                [FLINT_AND_STEEL]
                ignore-groups=admins, mods
                ignore-perms=own.permission,other.permission
                comment=Rule version=2
                message=&cYou cannot use %s = banned.
                on-use=deny,tell
                """);
        BlacklistRules.Rule rule = rules.matching("flint_and_steel").getFirst();
        assertEquals(Set.of("admins", "mods"), rule.ignoreGroups());
        assertEquals(Set.of("own.permission", "other.permission"), rule.ignorePermissions());
        assertEquals("Rule version=2", rule.comment());
        assertEquals("&cYou cannot use %s = banned.", rule.message());
    }

    @Test void malformedRulesFailClosedWithLineNumbers() {
        for (String invalid : List.of("on-break=deny", "[stone]\non-break=typo", "[stone]\nunknown=value", "[stone", "[]", "[bad space]", "[stone]\non-break")) {
            IOException exception = assertThrows(IOException.class, () -> BlacklistRules.parse(invalid));
            assertTrue(exception.getMessage().contains("line"));
        }
    }

    @Test void emptyBlacklistAndCommentsPermitActions() throws IOException {
        assertTrue(BlacklistRules.parse("# examples only\n; ignored\n").allows("stone", "on-break", false));
        assertTrue(BlacklistRules.empty().allows("stone", "on-break", true));
    }

    @Test void returnedRulesAreImmutable() throws IOException {
        BlacklistRules.Rule rule = BlacklistRules.parse("[stone]\non-break=deny").rules().getFirst();
        assertThrows(UnsupportedOperationException.class, () -> rule.targets().add("minecraft:dirt"));
        assertThrows(UnsupportedOperationException.class, () -> rule.actions().put("on-use", List.of("allow")));
        assertThrows(UnsupportedOperationException.class, () -> rule.actions("on-break").add("allow"));
    }
}
