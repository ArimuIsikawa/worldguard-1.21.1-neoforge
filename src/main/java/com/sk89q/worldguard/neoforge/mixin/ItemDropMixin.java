package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ItemDropMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void worldguard$drop(boolean wholeStack, CallbackInfoReturnable<Boolean> callback) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!ProtectionHooks.allowDrop(player) || !ProtectionHooks.blacklist(player, "on-drop", player.getInventory().getSelected())) callback.setReturnValue(false);
    }
}
