package com.sk89q.worldguard.neoforge.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Optional group providers integrate here; vanilla operator groups work without a provider. */
public final class RegionGroupEvent extends Event {
    private final ServerPlayer player;
    private final String group;
    private boolean member;
    public RegionGroupEvent(ServerPlayer player, String group, boolean member) { this.player=player; this.group=group; this.member=member; }
    public ServerPlayer player() { return player; }
    public String group() { return group; }
    public boolean isMember() { return member; }
    public void setMember(boolean member) { this.member=member; }
}
