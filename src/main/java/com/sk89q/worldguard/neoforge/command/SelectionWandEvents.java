package com.sk89q.worldguard.neoforge.command;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Server-only selection using the vanilla wooden axe; no client mod or OP required. */
public final class SelectionWandEvents {
    private static boolean selecting(PlayerInteractEvent event) {
        return WorldGuardMod.service() != null && event.getEntity() instanceof ServerPlayer
                && event.getHand() == InteractionHand.MAIN_HAND && event.getItemStack().is(Items.WOODEN_AXE)
                && event.getLevel().hasChunkAt(event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!selecting(event)) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (WorldEditSelectionBridge.handlesSelectionWand(player)) {
            if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) RegionCommands.clearSelection(player.getUUID());
            return;
        }
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) RegionCommands.wandPosition(player, 1, event.getPos());
        event.setCanceled(true);
        player.connection.send(new ClientboundBlockUpdatePacket(event.getPos(), event.getLevel().getBlockState(event.getPos())));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!selecting(event)) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (!RegionCommands.wandPosition(player, 2, event.getPos())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        player.connection.send(new ClientboundBlockUpdatePacket(event.getPos(), event.getLevel().getBlockState(event.getPos())));
    }
}
