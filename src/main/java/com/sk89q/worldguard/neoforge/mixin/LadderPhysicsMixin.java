package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.rules.WorldRulesHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LadderBlock.class)
public abstract class LadderPhysicsMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void worldguard$ladder(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (level instanceof ServerLevel server && WorldRulesHooks.bool(server, "physics.vine-like-rope-ladders")
                && level.getBlockState(pos.above()).is(Blocks.LADDER)) callback.setReturnValue(true);
    }
}
