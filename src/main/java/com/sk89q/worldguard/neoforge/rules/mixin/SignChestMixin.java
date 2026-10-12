package com.sk89q.worldguard.neoforge.rules.mixin;

import com.sk89q.worldguard.neoforge.rules.SignChestProtection;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SignChestMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "updateSignText", at = @At("HEAD"), cancellable = true)
    private void worldguard$sign(ServerboundSignUpdatePacket packet, List<FilteredText> filtered, CallbackInfo callback) {
        if (!player.serverLevel().hasChunkAt(packet.getPos())) return;
        String[] proposed = filtered.stream().map(FilteredText::raw).toArray(String[]::new);
        if (WorldGuardMod.service() != null && !WorldGuardMod.service().canUse(player.serverLevel(), packet.getPos(), player, "build")) {
            WorldGuardMod.service().deny(player, "edit a sign");
            worldguard$restore(packet); callback.cancel();
        } else if (!SignChestProtection.mayEdit(player.serverLevel(), packet.getPos(), player, packet.isFrontText(), proposed)) {
            worldguard$restore(packet); callback.cancel();
        }
    }

    private void worldguard$restore(ServerboundSignUpdatePacket packet) {
        var entity = player.serverLevel().getBlockEntity(packet.getPos());
        if (entity != null && entity.getUpdatePacket() != null) player.connection.send(entity.getUpdatePacket());
    }
}
