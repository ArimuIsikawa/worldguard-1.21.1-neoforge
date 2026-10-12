package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(HopperBlockEntity.class)
public abstract class HopperMixin {
    @Inject(method = "pushItemsTick", at = @At("HEAD"), cancellable = true)
    private static void worldguard$halt(Level level, BlockPos pos, BlockState state, HopperBlockEntity hopper, CallbackInfo callback) {
        if (level instanceof ServerLevel && WorldGuardMod.service() != null && WorldGuardMod.service().halted()) callback.cancel();
    }

    @Inject(method = "ejectItems", at = @At("HEAD"), cancellable = true)
    private static void worldguard$push(Level level, BlockPos pos, HopperBlockEntity hopper, CallbackInfoReturnable<Boolean> callback) {
        if (level instanceof ServerLevel server && (!ProtectionHooks.inventory(server, pos, pos)
                || !ProtectionHooks.inventory(server, pos, pos.relative(hopper.getBlockState().getValue(HopperBlock.FACING))))) {
            if (WorldGuardMod.service() != null) WorldGuardMod.service().hopperDenied(server,pos);
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true)
    private static void worldguard$pull(Level level, Hopper hopper, CallbackInfoReturnable<Boolean> callback) {
        BlockPos target = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY(), hopper.getLevelZ());
        if (level instanceof ServerLevel server && (!ProtectionHooks.inventory(server, target, target)
                || !ProtectionHooks.inventory(server, target, target.above()))) {
            if (WorldGuardMod.service() != null) WorldGuardMod.service().hopperDenied(server,target);
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z", at = @At("HEAD"), cancellable = true)
    private static void worldguard$pickup(Container target, ItemEntity item, CallbackInfoReturnable<Boolean> callback) {
        if (!(item.level() instanceof ServerLevel server)) return;
        BlockPos destination = target instanceof BlockEntity block ? block.getBlockPos()
                : target instanceof Entity entity ? entity.blockPosition() : null;
        if (destination != null && (!ProtectionHooks.inventory(server, destination, item.blockPosition())
                || WorldGuardMod.service() != null && !WorldGuardMod.service().canAutomate(server, destination, item.blockPosition(), "item-pickup", false))) callback.setReturnValue(false);
    }
}
