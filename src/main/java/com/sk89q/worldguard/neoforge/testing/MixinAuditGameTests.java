package com.sk89q.worldguard.neoforge.testing;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.spongepowered.asm.mixin.MixinEnvironment;

@GameTestHolder(WorldGuardMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MixinAuditGameTests {
    private MixinAuditGameTests() { }

    /** Audit forces transformation of targets that ordinary gameplay has not loaded yet. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void allConfiguredMixinTargetsTransform(GameTestHelper helper) {
        MixinEnvironment.getCurrentEnvironment().audit();
        helper.succeed();
    }
}
