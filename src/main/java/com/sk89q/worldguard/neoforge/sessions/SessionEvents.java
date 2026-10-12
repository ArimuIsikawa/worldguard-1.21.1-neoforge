package com.sk89q.worldguard.neoforge.sessions;

import com.sk89q.worldguard.neoforge.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import com.sk89q.worldguard.neoforge.admin.AdministrationCommands;
import java.util.Set;

public final class SessionEvents {
    @SubscribeEvent public void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && WorldGuardMod.service() != null) WorldGuardMod.service().sessions().death(player);
    }
    @SubscribeEvent public void halt(EntityTickEvent.Pre event) {
        var service = WorldGuardMod.service();
        if (service != null && !event.getEntity().level().isClientSide && service.halted() && AdministrationCommands.intensive(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && WorldGuardMod.service() != null) WorldGuardMod.service().sessions().tick(player);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && WorldGuardMod.service() != null) WorldGuardMod.service().logout(player);
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        ProtectionService service = WorldGuardMod.service();
        if (service == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (service.config().bool(null, "security.deop-everyone-on-join", false)) service.server().getPlayerList().deop(player.getGameProfile());
        if (service.config().bool(null, "auto-invincible", service.config().bool(null, "auto-invincible-permission", false))
                && service.hasPermission(player,"worldguard.auto-invincible")) service.god(player, true);
        if (service.config().bool(null,"auto-invincible-group",false) && service.inGroup(player,"wg-invincible")) service.god(player,true);
        if (service.config().bool(null,"auto-no-drowning-group",false) && service.inGroup(player,"wg-amphibious")) service.waterBreathing(player,true);
        if (service.config().bool(null, "regions.announce-bypass-status", false))
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("WorldGuard bypass: " + service.bypass(player)));
    }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && WorldGuardMod.service() != null) WorldGuardMod.service().sessions().respawn(player);
    }
    @SubscribeEvent public void chat(ServerChatEvent event) {
        ProtectionService service = WorldGuardMod.service();
        if (service != null && !service.testState(event.getPlayer().serverLevel(), event.getPlayer().blockPosition(), event.getPlayer(), "send-chat")) {
            event.setCanceled(true); service.deny(event.getPlayer(), "писать в чат");
        }
        if (service != null && service.config().bool(event.getPlayer().serverLevel().dimension().location().toString(),"regions.cancel-chat-without-recipients",true)
                && service.server().getPlayerList().getPlayers().stream().noneMatch(player -> service.testState(player.serverLevel(),player.blockPosition(),player,"receive-chat"))) event.setCanceled(true);
    }
    @SubscribeEvent public void command(CommandEvent event) {
        ProtectionService service = WorldGuardMod.service();
        if (service == null || !(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)) return;
        String command = event.getParseResults().getReader().getString();
        String root = CommandPatterns.normalize(command).split(" ", 2)[0];
        if (service.config().bool(null, "security.block-in-game-op-command", false) && (root.equals("op") || root.equals("deop"))) {
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("/op and /deop are restricted to the server console."));
            return;
        }
        if (service.bypass(player)) return;
        Set<String> blocked = service.setValues(player.serverLevel(), player.blockPosition(), player, "blocked-cmds");
        Set<String> allowed = service.setValues(player.serverLevel(), player.blockPosition(), player, "allowed-cmds");
        boolean permitted = CommandPatterns.matches(allowed, command);
        if (!permitted && CommandPatterns.matches(blocked, command)) { event.setCanceled(true); service.deny(player, "выполнять эту команду"); }
    }
}
