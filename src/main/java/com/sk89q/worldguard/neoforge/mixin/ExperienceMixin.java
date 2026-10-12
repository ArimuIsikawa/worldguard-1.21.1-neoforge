package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class ExperienceMixin {
    @Inject(method = "popExperience", at = @At("HEAD"), cancellable = true)
    private void worldguard$experience(ServerLevel level, BlockPos pos, int amount, CallbackInfo callback) {
        if (!ProtectionEvents.flag(level, pos, null, "exp-drops")) callback.cancel();
    }
}
