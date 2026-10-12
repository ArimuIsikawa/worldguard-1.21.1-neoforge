package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = {"net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal", "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal"})
public abstract class EndermanBlocksMixin {
    @Shadow(remap = false) @Final private EnderMan enderman;

    @WrapMethod(method = "tick")
    private void worldguard$blocks(Operation<Void> original) {
        try (var scope = ProtectionHooks.scope("mob", enderman)) { original.call(); }
    }
}
