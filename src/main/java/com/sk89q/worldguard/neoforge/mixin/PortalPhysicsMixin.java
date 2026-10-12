package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.rules.WorldRulesHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetherPortalBlock.class)
public abstract class PortalPhysicsMixin {
    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void worldguard$portal(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos, CallbackInfoReturnable<BlockState> callback) {
        if (level instanceof ServerLevel server && WorldRulesHooks.bool(server, "physics.allow-portal-anywhere")) callback.setReturnValue(state);
    }
}
