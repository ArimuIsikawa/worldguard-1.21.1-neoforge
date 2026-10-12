package com.sk89q.worldguard.neoforge.sessions;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPatternsTest {
    @Test
    void normalizesBothNamespaceAndLeadingSlashWithoutChangingArgumentNamespaces() {
        assertEquals("give alex minecraft:stone", CommandPatterns.normalize(" /MiNeCrAfT:Give  Alex\tMinecraft:Stone "));
        assertTrue(CommandPatterns.matches("/minecraft:give Alex", "give Alex minecraft:stone"));
        assertTrue(CommandPatterns.matches("give Alex", "/minecraft:give Alex minecraft:stone"));
    }

    @Test
    void commandAndArgumentPrefixesRespectTokenBoundaries() {
        assertTrue(CommandPatterns.matches("kill", "minecraft:kill @e"));
        assertFalse(CommandPatterns.matches("kill", "killer @e"));
        assertTrue(CommandPatterns.matches("execute as alex", "minecraft:execute   as\tAlex run say hello"));
        assertFalse(CommandPatterns.matches("execute as alex", "execute as alexander run say hello"));
    }

    @Test
    void supportsAllAndTrailingWildcards() {
        assertTrue(CommandPatterns.matches("*", "minecraft:kill @e"));
        assertTrue(CommandPatterns.matches("region*", "regions list"));
        assertTrue(CommandPatterns.matches("/minecraft:execute as *", "execute as Alex run kill @e"));
        assertFalse(CommandPatterns.matches("execute as *", "execute at Alex run kill @e"));
    }

    @Test
    void emptyPatternsDoNotAuthorizeAndCollectionsNormalizeEveryPattern() {
        assertFalse(CommandPatterns.matches(" ", "say hello"));
        assertFalse(CommandPatterns.matches("*", " "));
        assertTrue(CommandPatterns.matches(Set.of("/mod:noop", " /minecraft:kill "), "kill @e"));
        assertFalse(CommandPatterns.matches(Set.of("/mod:noop", " /minecraft:kill "), "give Alex minecraft:stone"));
    }
}
