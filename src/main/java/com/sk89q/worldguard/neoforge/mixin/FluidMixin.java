package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowingFluid.class)
public abstract class FluidMixin {
    @Inject(method = "canSpreadTo", at = @At("HEAD"), cancellable = true)
    private void worldguard$flow(BlockGetter level, BlockPos source, BlockState sourceState, Direction direction,
                                BlockPos destination, BlockState state, FluidState fluid, Fluid incoming,
                                CallbackInfoReturnable<Boolean> callback) {
        if (!(level instanceof ServerLevel server)) return;
        if (WorldGuardMod.service() != null && WorldGuardMod.service().halted()) { callback.setReturnValue(false); return; }
        if (!com.sk89q.worldguard.neoforge.rules.WorldRulesHooks.flow(server, source, destination, state, incoming)) { callback.setReturnValue(false); return; }
        if (WorldGuardMod.service() != null && WorldGuardMod.service().config().bool(server.dimension().location().toString(), "regions.protect-against-liquid-flow", false)
                && !com.sk89q.worldguard.neoforge.protection.ProtectionHooks.canCrossBoundary(server, source, destination)) {
            callback.setReturnValue(false); return;
        }
        String flag = incoming.is(FluidTags.LAVA) ? "lava-flow" : "water-flow";
        if (!ProtectionEvents.flag(server, source, null, flag) || !ProtectionEvents.flag(server, destination, null, flag)) callback.setReturnValue(false);
    }
}
