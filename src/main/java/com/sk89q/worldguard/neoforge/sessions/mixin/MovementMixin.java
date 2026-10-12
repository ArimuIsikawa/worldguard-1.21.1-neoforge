package com.sk89q.worldguard.neoforge.sessions.mixin;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MovementMixin {
    @Shadow public ServerPlayer player;
    private static final String THREAD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";
    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void worldguard$move(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        var service = WorldGuardMod.service();
        if (service == null || !packet.hasPosition()) return;
        double x = packet.getX(player.getX()), y = packet.getY(player.getY()), z = packet.getZ(player.getZ());
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return; // vanilla handles malformed packets
        if (!service.sessions().canTravel(player, player.serverLevel(), BlockPos.containing(x, y, z), false)) {
            service.sessions().force(player, () -> player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
            ci.cancel();
        }
    }
    @Inject(method = "handleMoveVehicle", at = @At(value = "INVOKE", target = THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void worldguard$vehicle(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        var service = WorldGuardMod.service();
        Entity vehicle = player.getRootVehicle();
        if (service == null || vehicle == player || vehicle.getControllingPassenger() != player) return;
        if (!Double.isFinite(packet.getX()) || !Double.isFinite(packet.getY()) || !Double.isFinite(packet.getZ())) return;
        if (!service.sessions().canTravel(player, player.serverLevel(), BlockPos.containing(packet.getX(), packet.getY(), packet.getZ()), false)) {
            player.connection.send(new ClientboundMoveVehiclePacket(vehicle)); ci.cancel();
        }
    }
}
