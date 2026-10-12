package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LavaFluid.class)
public abstract class LavaFireMixin {
    @WrapMethod(method = "randomTick")
    private void worldguard$fire(Level level, BlockPos pos, FluidState state, RandomSource random, Operation<Void> original) {
        try (var scope = ProtectionHooks.scope("lava-fire", null,pos)) { original.call(level, pos, state, random); }
    }
}
