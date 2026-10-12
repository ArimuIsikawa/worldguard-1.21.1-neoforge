package com.sk89q.worldguard.neoforge.sessions.mixin;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class PlayerTeleportMixin {
    private boolean worldguard$allowed(ServerLevel level, double x, double y, double z) {
        var service = WorldGuardMod.service();
        ServerPlayer player = (ServerPlayer) (Object) this;
        return service == null || service.sessions().canTravel(player, level, BlockPos.containing(x, y, z), true);
    }
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void worldguard$teleport(ServerLevel level, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        if (!worldguard$allowed(level, x, y, z)) ci.cancel();
    }
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void worldguard$relativeTeleport(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relative, float yaw, float pitch, CallbackInfoReturnable<Boolean> ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        // RelativeMovement describes client packet deltas; this method's position arguments are already absolute.
        if (!worldguard$allowed(level, x, y, z)) ci.setReturnValue(false);
    }
    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void worldguard$dimension(DimensionTransition transition, CallbackInfoReturnable<Entity> ci) {
        if (!worldguard$allowed(transition.newLevel(), transition.pos().x, transition.pos().y, transition.pos().z)) ci.setReturnValue(null);
    }
}
