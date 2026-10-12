package com.sk89q.worldguard.neoforge.rules;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HostKeyRulesTest {
    @Test void uuidTakesPrecedenceAndNamesIgnoreCase() {
        UUID uuid = UUID.randomUUID();
        assertEquals("uuid-key", HostKeyRules.expected(Map.of("Alice", "name-key", uuid.toString(), "uuid-key"), uuid, "alice"));
        assertEquals("name-key", HostKeyRules.expected(Map.of("Alice", "name-key"), uuid, "ALICE"));
        assertNull(HostKeyRules.expected(Map.of("Alice", "name-key"), uuid, "Bob"));
    }
    @Test void missingHandshakeCannotBypassAConfiguredKey() {
        assertFalse(HostKeyRules.matches("private.example", null, false));
        assertFalse(HostKeyRules.matches("private.example", "other.example", true));
        assertTrue(HostKeyRules.matches(null, null, false));
        assertTrue(HostKeyRules.matches("private.example", "private.example:25565", false));
    }
    @Test void forgeSuffixRequiresItsExplicitOptionAndCannotHideAnotherHost() {
        assertFalse(HostKeyRules.matches("private.example", "private.example\u0000FML2\u0000", false));
        assertTrue(HostKeyRules.matches("private.example", "private.example\u0000FML2\u0000", true));
        assertTrue(HostKeyRules.matches("private.example", "private.example\u0000FML3\u0000", true));
        assertFalse(HostKeyRules.matches("private.example", "other.example\u0000FML2\u0000", true));
    }
}
