package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Mob.class)
public abstract class MobAiBlocksMixin {
    @WrapMethod(method = "serverAiStep")
    private void worldguard$grief(Operation<Void> original) {
        try (var scope = ProtectionHooks.scope("mob", (Mob) (Object) this)) { original.call(); }
    }
}
