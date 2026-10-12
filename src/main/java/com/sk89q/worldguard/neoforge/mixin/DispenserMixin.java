package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({DispenserBlock.class, DropperBlock.class})
public abstract class DispenserMixin {
    @WrapMethod(method = "dispenseFrom")
    private void worldguard$dispense(ServerLevel level, BlockState state, BlockPos source, Operation<Void> original) {
        BlockPos target = source.relative(state.getValue(DispenserBlock.FACING));
        if (!ProtectionHooks.canCrossBoundary(level, source, target)) return;
        // Boundary checks run before the dispenser removes the selected inventory stack.
        try (var scope = ProtectionHooks.scope(null, null)) { original.call(level, state, source); }
    }

    @WrapOperation(method = "dispenseFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/DispenserBlockEntity;getRandomSlot(Lnet/minecraft/util/RandomSource;)I"))
    private int worldguard$blacklist(DispenserBlockEntity dispenser, RandomSource random, Operation<Integer> original) {
        int slot = original.call(dispenser, random);
        if (slot >= 0 && dispenser.getLevel() instanceof ServerLevel level && WorldGuardMod.service() != null
                && !WorldGuardMod.service().blacklist().check(level, dispenser.getBlockPos(), null, "on-dispense", dispenser.getItem(slot))) return -1;
        return slot;
    }
}
