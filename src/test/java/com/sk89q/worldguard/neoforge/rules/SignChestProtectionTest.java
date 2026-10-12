package com.sk89q.worldguard.neoforge.rules;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SignChestProtectionTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void lockOwnersIncludeNamesAndUuidsButNeverAnonymousAutomation() {
        String[] sign = {"[Lock]", "Alice", "uuid:00000000-0000-0000-0000-000000000001", "Bob"};
        assertTrue(SignOwners.allows(sign, ALICE, "alice"));
        assertTrue(SignOwners.allows(sign, null, "BOB"));
        assertTrue(SignOwners.allows(sign, ALICE, "RenamedAlice"));
        assertFalse(SignOwners.allows(sign, UUID.randomUUID(), "Mallory"));
        assertFalse(SignOwners.allows(sign, null, null));
    }

    @Test void blankOwnerLinesCannotGrantAccessAndOrdinarySignsRemainEditable() {
        assertFalse(SignOwners.allows(new String[]{"[lock]", "", " ", ""}, null, ""));
        assertTrue(SignOwners.allows(new String[]{"Welcome", "Alice", "", ""}, null, null));
        assertTrue(SignOwners.isLock(new String[]{" [LOCK] ", "Alice"}));
    }
}
