package com.sk89q.worldguard.neoforge.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Native permission provider hook; its initial answer is the configured vanilla OP level. */
public final class RegionPermissionEvent extends Event {
    private final ServerPlayer player;
    private final String permission;
    private boolean allowed;
    public RegionPermissionEvent(ServerPlayer player, String permission, boolean allowed) { this.player=player; this.permission=permission; this.allowed=allowed; }
    public ServerPlayer player() { return player; }
    public String permission() { return permission; }
    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed=allowed; }
}
