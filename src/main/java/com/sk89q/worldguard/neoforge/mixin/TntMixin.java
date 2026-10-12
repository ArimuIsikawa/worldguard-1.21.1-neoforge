package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TntBlock.class)
public abstract class TntMixin {
    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void worldguard$place(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving, CallbackInfo callback) {
        if (level instanceof ServerLevel server && !ProtectionEvents.flag(server, pos, null, "tnt")) callback.cancel();
    }

    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void worldguard$redstone(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean moving, CallbackInfo callback) {
        if (level instanceof ServerLevel server && !ProtectionEvents.flag(server, pos, null, "tnt")) callback.cancel();
    }

    @Inject(method = "explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private static void worldguard$prime(Level level, BlockPos pos, net.minecraft.world.entity.LivingEntity igniter, CallbackInfo callback) {
        if (level instanceof ServerLevel server && !ProtectionEvents.flag(server, pos, igniter, "tnt")) callback.cancel();
    }
}
