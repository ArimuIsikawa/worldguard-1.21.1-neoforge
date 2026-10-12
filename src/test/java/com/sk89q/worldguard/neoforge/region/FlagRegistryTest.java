package com.sk89q.worldguard.neoforge.region;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FlagRegistryTest {
    @Test void parsesTypesAndGroupAliases() {
        assertEquals("deny", FlagRegistry.parse("pvp", "DENY"));
        assertNull(FlagRegistry.parse("pvp", "none"));
        assertEquals(true, FlagRegistry.parse("notify-enter", "true"));
        assertEquals(5, FlagRegistry.parse("heal-delay", "5"));
        assertEquals(2.5, FlagRegistry.parse("heal-min-health", "2.5"));
        assertEquals("nonmembers", FlagRegistry.parse("pvp-group", "non-members"));
        assertEquals("hello world", FlagRegistry.parse("greeting", "hello world"));
    }

    @Test void preservesTypedLocationsAndNormalizesIdentifiers() {
        assertEquals(Set.of("minecraft:zombie", "mod:mob"), FlagRegistry.parse("deny-spawn", "zombie,mod:mob"));
        assertEquals(Set.of("/kill", "/stop"), FlagRegistry.parse("blocked-cmds", "kill,/stop"));
        Map<?, ?> location = (Map<?, ?>) FlagRegistry.parse("teleport", "1,2,3,90,45,minecraft:overworld");
        assertEquals("minecraft:overworld", location.get("world")); assertEquals(90d, location.get("yaw"));
        assertEquals("block-break", FlagRegistry.canonicalName("BLOCKBREAK"));
    }

    @Test void rejectsBadValuesRatherThanIgnoringProtection() {
        assertThrows(IllegalArgumentException.class, () -> FlagRegistry.parse("pvp", "true"));
        assertThrows(IllegalArgumentException.class, () -> FlagRegistry.parse("heal-delay", "1.5"));
        assertThrows(IllegalArgumentException.class, () -> FlagRegistry.parse("heal-delay", "2147483648"));
        assertThrows(IllegalArgumentException.class, () -> FlagRegistry.parse("heal-min-health", "NaN"));
        assertThrows(IllegalArgumentException.class, () -> FlagRegistry.normalize("deny-spawn", "zombie"));
        assertThrows(IllegalArgumentException.class, () -> FlagRegistry.parse("game-mode", "god"));
        assertThrows(IllegalArgumentException.class, () -> FlagRegistry.parse("weather-lock", "snow"));
        assertThrows(IllegalArgumentException.class, () -> FlagRegistry.normalize("greeting", List.of("hello")));
    }

    @Test void defaultsMatchWorldGuardBuildAndMovementRules() {
        assertEquals("allow", FlagRegistry.defaultValue("build"));
        assertNull(FlagRegistry.defaultValue("pvp"));
        assertEquals("allow", FlagRegistry.defaultValue("mob-spawning"));
        assertEquals("nonmembers", FlagRegistry.defaultGroup("entry"));
        assertEquals("members", FlagRegistry.defaultGroup("spawn"));
    }
}
