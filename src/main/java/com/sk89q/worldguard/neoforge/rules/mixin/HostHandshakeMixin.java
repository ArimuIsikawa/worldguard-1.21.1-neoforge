package com.sk89q.worldguard.neoforge.rules.mixin;

import com.sk89q.worldguard.neoforge.rules.HostKeyService;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerHandshakePacketListenerImpl.class)
public abstract class HostHandshakeMixin {
    @Shadow @Final private Connection connection;
    @Inject(method = "handleIntention", at = @At("HEAD"))
    private void worldguard$hostname(ClientIntentionPacket packet, CallbackInfo callback) {
        HostKeyService.handshake(connection, packet.hostName());
    }
}
