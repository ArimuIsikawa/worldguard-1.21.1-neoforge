package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BigDripleafBlock.class)
public abstract class DripleafMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void worldguard$stand(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo callback) {
        if (level instanceof ServerLevel server && !ProtectionEvents.use(server, pos, ProtectionEvents.responsible(entity), "use-dripleaf")) callback.cancel();
    }

    @Inject(method = "onProjectileHit", at = @At("HEAD"), cancellable = true)
    private void worldguard$projectile(Level level, BlockState state, BlockHitResult hit, Projectile projectile, CallbackInfo callback) {
        if (level instanceof ServerLevel server && !ProtectionEvents.use(server, hit.getBlockPos(), ProtectionEvents.responsible(projectile), "use-dripleaf")) callback.cancel();
    }
}
