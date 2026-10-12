package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockInteractionMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void worldguard$redstone(Level level, BlockPos pos, Entity actor, CallbackInfo callback) {
        if (!(level instanceof ServerLevel server)) return;
        Block block = ((BlockState) (Object) this).getBlock();
        if (com.sk89q.worldguard.neoforge.rules.WorldRulesHooks.matches(server, "event-handling.interaction-whitelist",
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString())) return;
        if ((block instanceof ButtonBlock || block instanceof BasePressurePlateBlock || block instanceof TripWireBlock)
                && !ProtectionEvents.use(server, pos, ProtectionEvents.responsible(actor), "use")) callback.cancel();
    }

    @Inject(method = "onProjectileHit", at = @At("HEAD"), cancellable = true)
    private void worldguard$projectile(Level level, BlockState state, BlockHitResult hit, Projectile projectile, CallbackInfo callback) {
        if (!(level instanceof ServerLevel server)) return;
        String flag = state.getBlock() instanceof TntBlock ? "tnt" : state.getBlock() instanceof BellBlock ? "use"
                : state.getBlock() instanceof CampfireBlock || state.getBlock() instanceof AbstractCandleBlock ? "lighter" : null;
        if (flag != null && !ProtectionEvents.use(server, hit.getBlockPos(), ProtectionEvents.responsible(projectile), flag)) callback.cancel();
    }
}
