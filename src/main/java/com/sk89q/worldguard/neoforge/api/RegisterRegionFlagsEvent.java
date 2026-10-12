package com.sk89q.worldguard.neoforge.api;

import com.sk89q.worldguard.neoforge.region.FlagRegistry;
import net.neoforged.bus.api.Event;

/** Fired on the NeoForge bus before region files are loaded, on every server start. */
public final class RegisterRegionFlagsEvent extends Event {
    public void register(String name, FlagRegistry.Type type, Object defaultValue, String group) {
        FlagRegistry.register(name,type,defaultValue,group);
    }
}
