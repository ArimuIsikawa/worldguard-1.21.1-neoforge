package com.sk89q.worldguard.neoforge.rules;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import java.util.Map;
import java.util.WeakHashMap;

public final class HostKeyService {
    private static final Map<Connection, String> HOSTS = new WeakHashMap<>();
    private HostKeyService() { }
    public static synchronized void handshake(Connection connection, String hostname) { HOSTS.put(connection, hostname); }
    public static synchronized boolean allows(Connection connection, GameProfile profile) {
        var service = WorldGuardMod.service();
        if (service == null) return true;
        String expected = HostKeyRules.expected(service.config().value(null, "host-keys"), profile.getId(), profile.getName());
        return HostKeyRules.matches(expected, HOSTS.remove(connection), service.config().bool(null, "security.host-keys-allow-forge-clients", false));
    }
}
