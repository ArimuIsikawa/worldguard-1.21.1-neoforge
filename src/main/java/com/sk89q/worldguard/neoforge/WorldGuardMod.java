package com.sk89q.worldguard.neoforge;

import com.mojang.logging.LogUtils;
import com.sk89q.worldguard.neoforge.command.RegionCommands;
import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import com.sk89q.worldguard.neoforge.sessions.SessionEvents;
import com.sk89q.worldguard.neoforge.rules.WorldRulesEvents;
import com.sk89q.worldguard.neoforge.rules.SignChestProtection;
import com.sk89q.worldguard.neoforge.admin.AdministrationCommands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import java.io.IOException;

@Mod(WorldGuardMod.MOD_ID)
public final class WorldGuardMod {
    public static final String MOD_ID = "worldguard";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static volatile ProtectionService service;

    public WorldGuardMod() {
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new ProtectionEvents());
        NeoForge.EVENT_BUS.register(new SessionEvents());
        NeoForge.EVENT_BUS.register(new WorldRulesEvents());
        NeoForge.EVENT_BUS.register(new SignChestProtection());
        NeoForge.EVENT_BUS.register(new com.sk89q.worldguard.neoforge.command.LegacyCommandAdapter());
        NeoForge.EVENT_BUS.register(new com.sk89q.worldguard.neoforge.command.SelectionWandEvents());
    }
    public static ProtectionService service() { return service; }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        RegionCommands.register(event.getDispatcher()); AdministrationCommands.register(event.getDispatcher());
    }
    @SubscribeEvent public void start(ServerStartingEvent event) {
        try {
            NeoForge.EVENT_BUS.post(new com.sk89q.worldguard.neoforge.api.RegisterRegionFlagsEvent());
            service = new ProtectionService(event.getServer(), FMLPaths.CONFIGDIR.get().resolve("worldguard"));
            if(service.config().bool(null,"summary-on-start",true)) LOGGER.info("WorldGuard region protection active; {} dimensions loaded; {} storage", service.repository().allDimensions().size(),service.repository().isSql() ? "SQL" : "YAML");
            service.beginUuidMigration();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("WorldGuard protection could not load. Repair the configuration or region YAML before starting the server.", e);
        }
    }
    @SubscribeEvent public void stop(ServerStoppingEvent event) {
        ProtectionService current = service;
        try { if (current != null) current.shutdown(); }
        catch (IOException e) { LOGGER.error("Unable to save WorldGuard regions", e); }
        finally { service = null; }
        RegionCommands.clearSelections();
    }
}
