package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({SnowGolem.class, EnderDragon.class})
public abstract class MobBlocksMixin {
    @WrapMethod(method = "aiStep")
    private void worldguard$blocks(Operation<Void> original) {
        try (var scope = ProtectionHooks.scope("mob", (Entity) (Object) this)) { original.call(); }
    }
}
