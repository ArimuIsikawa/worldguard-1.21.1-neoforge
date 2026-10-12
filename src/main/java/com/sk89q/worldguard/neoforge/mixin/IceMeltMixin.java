package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IceBlock.class)
public abstract class IceMeltMixin {
    @Inject(method = "melt", at = @At("HEAD"), cancellable = true)
    private void worldguard$melt(BlockState state, Level level, BlockPos pos, CallbackInfo callback) {
        String flag = state.getBlock() instanceof FrostedIceBlock ? "frosted-ice-melt" : "ice-melt";
        if (level instanceof ServerLevel server && !ProtectionEvents.flag(server, pos, null, flag)) callback.cancel();
    }
}
