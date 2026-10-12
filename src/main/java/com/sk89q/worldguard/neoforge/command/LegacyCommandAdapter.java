package com.sk89q.worldguard.neoforge.command;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;

public final class LegacyCommandAdapter {
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void command(CommandEvent event) {
        var service=WorldGuardMod.service(); if(service==null) return;
        String original=event.getParseResults().getReader().getString();
        var source=event.getParseResults().getContext().getSource();
        try {
            String translated=LegacyCommandSyntax.translate(original,service::resolveWorld);
            if(!translated.equals(original)) event.setParseResults(source.getServer().getCommands().getDispatcher().parse(translated,source));
        } catch(IllegalArgumentException exception) { event.setCanceled(true); source.sendFailure(Component.literal(exception.getMessage())); }
    }
}
