package com.sk89q.worldguard.neoforge.sessions.mixin;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class PacketListenerMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), argsOnly = true)
    private Packet<?> worldguard$visualFlags(Packet<?> packet) {
        var service = WorldGuardMod.service();
        if (service != null && (Object) this instanceof ServerGamePacketListenerImpl listener)
            return service.sessions().transformPacket(listener.player, packet);
        return packet;
    }
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
    private void worldguard$receiveChat(Packet<?> packet, PacketSendListener callback, CallbackInfo ci) {
        var service = WorldGuardMod.service();
        if (service != null && (packet instanceof ClientboundPlayerChatPacket || packet instanceof ClientboundDisguisedChatPacket)
            && (Object) this instanceof ServerGamePacketListenerImpl listener
            && !service.testState(listener.player.serverLevel(), listener.player.blockPosition(), listener.player, "receive-chat")) ci.cancel();
    }
}
