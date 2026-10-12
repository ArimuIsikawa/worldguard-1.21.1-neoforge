package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.OptionalInt;

@Mixin(ServerPlayer.class)
public abstract class EntityContainerMixin {
    @Inject(method = "openMenu", at = @At("HEAD"), cancellable = true)
    private void worldguard$open(MenuProvider provider, CallbackInfoReturnable<OptionalInt> callback) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (provider instanceof Entity target && !ProtectionEvents.use(player.serverLevel(), target.blockPosition(), player, "chest-access")) callback.setReturnValue(OptionalInt.empty());
    }

    @Inject(method = "openHorseInventory", at = @At("HEAD"), cancellable = true)
    private void worldguard$horse(AbstractHorse horse, Container container, CallbackInfo callback) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!ProtectionEvents.use(player.serverLevel(), horse.blockPosition(), player, "chest-access")) callback.cancel();
    }
}
