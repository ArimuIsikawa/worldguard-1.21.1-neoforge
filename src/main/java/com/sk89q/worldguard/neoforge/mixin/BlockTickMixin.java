package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockTickMixin {
    @WrapMethod(method = "randomTick")
    private void worldguard$random(ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
        worldguard$tick(level, pos, random, original, true);
    }

    @WrapMethod(method = "tick")
    private void worldguard$scheduled(ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
        worldguard$tick(level, pos, random, original, false);
    }

    private void worldguard$tick(ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original, boolean randomTick) {
        if (WorldGuardMod.service() != null && WorldGuardMod.service().halted()) return;
        if (!com.sk89q.worldguard.neoforge.rules.WorldRulesHooks.tick(level, pos, (BlockState) (Object) this)) return;
        String flag = ProtectionHooks.naturalFlag((BlockState) (Object) this, randomTick);
        // Farmland distinguishes moisture changes and drying at the target state transition.
        if (flag != null && !flag.equals("soil") && !ProtectionEvents.flag(level, pos, null, flag)) return;
        try (var scope = ProtectionHooks.scope(flag, null,pos)) { original.call(level, pos, random); }
    }
}
