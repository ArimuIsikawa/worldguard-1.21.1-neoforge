package com.sk89q.worldguard.neoforge.sessions.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    @Shadow private int foodLevel;
    @Shadow private float saturationLevel;
    @Shadow private float exhaustionLevel;
    @WrapMethod(method = "tick")
    private void worldguard$hunger(Player player, Operation<Void> original) {
        var service = WorldGuardMod.service();
        boolean freeze = player instanceof ServerPlayer sp && service != null
            && !service.testState(sp.serverLevel(), sp.blockPosition(), sp, "natural-hunger-drain");
        int food = foodLevel; float saturation = saturationLevel, exhaustion = exhaustionLevel;
        try { original.call(player); }
        finally {
            if (freeze) { foodLevel = food; saturationLevel = saturation; exhaustionLevel = Math.min(exhaustion, 4); }
        }
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isHurt()Z"))
    private boolean worldguard$regeneration(Player player) {
        var service = WorldGuardMod.service();
        return player.isHurt() && (!(player instanceof ServerPlayer sp) || service == null
            || service.testState(sp.serverLevel(), sp.blockPosition(), sp, "natural-health-regen"));
    }
}
