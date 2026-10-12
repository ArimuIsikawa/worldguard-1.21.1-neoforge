package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public abstract class EndermanPlaceMixin {
    @Shadow @Final private EnderMan enderman;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"), cancellable = true)
    private void worldguard$place(CallbackInfo callback, @Local(ordinal = 0) BlockPos pos) {
        if (enderman.level() instanceof ServerLevel level && !ProtectionHooks.mobChange(level, pos, enderman, "block-place")) callback.cancel();
    }
}
