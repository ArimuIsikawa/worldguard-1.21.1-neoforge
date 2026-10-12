package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AreaEffectCloud.class)
public abstract class LingeringPotionMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean worldguard$effect(LivingEntity target, MobEffectInstance effect, Entity source, Operation<Boolean> original) {
        return ProtectionHooks.potion(target, ((AreaEffectCloud) (Object) this).getOwner(), effect.getEffect().value()) && original.call(target, effect, source);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffect;applyInstantenousEffect(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"))
    private void worldguard$instant(MobEffect effect, Entity source, Entity owner, LivingEntity target, int amplifier, double strength, Operation<Void> original) {
        if (ProtectionHooks.potion(target, owner, effect)) original.call(effect, source, owner, target, amplifier, strength);
    }
}
