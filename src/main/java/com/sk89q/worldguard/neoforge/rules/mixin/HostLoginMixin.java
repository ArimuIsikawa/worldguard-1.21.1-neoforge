package com.sk89q.worldguard.neoforge.rules.mixin;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import com.sk89q.worldguard.neoforge.rules.HostKeyService;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class HostLoginMixin {
    @Inject(method = "placeNewPlayer", at = @At("HEAD"), cancellable = true)
    private void worldguard$hostKey(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo callback) {
        if (!HostKeyService.allows(connection, player.getGameProfile())) {
            Component reason = Component.literal("You did not join with the valid WorldGuard host key.");
            if (connection.getPacketListener() instanceof ServerCommonPacketListenerImpl listener) listener.disconnect(reason);
            else connection.disconnect(reason);
            WorldGuardMod.LOGGER.warn("Rejected {}: WorldGuard host key check failed", player.getGameProfile().getName());
            callback.cancel();
        }
    }
}
