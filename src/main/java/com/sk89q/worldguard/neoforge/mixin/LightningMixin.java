package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightningBolt.class)
public abstract class LightningMixin {
    @WrapMethod(method = "tick")
    private void worldguard$lightning(Operation<Void> original) {
        try (var scope = ProtectionHooks.scope("lightning", null)) { original.call(); }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;thunderHit(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LightningBolt;)V"))
    private void worldguard$hit(Entity target, ServerLevel level, LightningBolt lightning, Operation<Void> original) {
        if (ProtectionEvents.flag(level, target.blockPosition(), null, "lightning")) original.call(target, level, lightning);
    }
}
