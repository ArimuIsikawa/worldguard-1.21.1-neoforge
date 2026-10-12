package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({VehicleEntity.class, BlockAttachedEntity.class, ItemFrame.class, ArmorStand.class})
public abstract class DecorationDamageMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void worldguard$damage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (!ProtectionHooks.allowNonlivingDamage((Entity) (Object) this, source)) callback.setReturnValue(false);
    }
}
