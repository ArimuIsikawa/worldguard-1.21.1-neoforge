package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public abstract class DeathMessageMixin {
    @WrapOperation(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean worldguard$death(GameRules rules, GameRules.Key<GameRules.BooleanValue> key, Operation<Boolean> original) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (key == GameRules.RULE_SHOWDEATHMESSAGES && WorldGuardMod.service() != null
                && WorldGuardMod.service().config().bool(player.serverLevel().dimension().location().toString(), "player-damage.disable-death-messages", false)) return false;
        return original.call(rules, key);
    }
}
