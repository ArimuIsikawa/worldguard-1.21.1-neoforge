package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SculkSpreader;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SculkSpreader.class)
public abstract class SculkMixin {
    @WrapMethod(method = "updateCursors")
    private void worldguard$growth(LevelAccessor level, BlockPos pos, RandomSource random, boolean spread, Operation<Void> original) {
        if (WorldGuardMod.service() != null && WorldGuardMod.service().halted()) return;
        try (var scope = ProtectionHooks.scope("sculk-growth", null)) { original.call(level, pos, random, spread); }
    }
}
