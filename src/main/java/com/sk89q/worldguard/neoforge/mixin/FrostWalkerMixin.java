package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.ReplaceDisk;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ReplaceDisk.class)
public abstract class FrostWalkerMixin {
    @WrapMethod(method = "apply")
    private void worldguard$freeze(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity actor, Vec3 origin, Operation<Void> original) {
        try (var scope = ProtectionHooks.scope("enchantment", actor)) { original.call(level, enchantmentLevel, item, actor, origin); }
    }
}
