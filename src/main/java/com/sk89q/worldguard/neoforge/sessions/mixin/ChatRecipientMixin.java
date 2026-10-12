package com.sk89q.worldguard.neoforge.sessions.mixin;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reject before vanilla records a signed message as sent to the recipient. */
@Mixin(ServerPlayer.class)
public abstract class ChatRecipientMixin {
    @Inject(method="sendChatMessage",at=@At("HEAD"),cancellable=true)
    private void worldguard$recipient(OutgoingChatMessage message, boolean filtered, ChatType.Bound type, CallbackInfo callback) {
        var service=WorldGuardMod.service(); ServerPlayer player=(ServerPlayer)(Object)this;
        if (service != null && !service.testState(player.serverLevel(),player.blockPosition(),player,"receive-chat")) callback.cancel();
    }
}
