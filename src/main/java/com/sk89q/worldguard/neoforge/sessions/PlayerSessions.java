package com.sk89q.worldguard.neoforge.sessions;

import com.sk89q.worldguard.neoforge.*;
import com.sk89q.worldguard.neoforge.region.Region;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class PlayerSessions {
    private final ProtectionService service;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> forceTravel = new HashSet<>();
    public PlayerSessions(ProtectionService service) { this.service = service; }
    public boolean forced(ServerPlayer player) { return forceTravel.contains(player.getUUID()); }
    public void force(ServerPlayer player, Runnable action) {
        boolean added = forceTravel.add(player.getUUID());
        try { action.run(); } finally { if (added) forceTravel.remove(player.getUUID()); }
    }
    public boolean canTravel(ServerPlayer player, ServerLevel target, BlockPos position, boolean teleport) {
        if (forced(player) || service.bypass(player)) return true;
        if (!service.config().bool(null,teleport ? "use-player-teleports" : "use-player-move-event",true)) return true;
        if (!service.canEnter(player, target, position)) { service.deny(player, target, position, "entry-deny-message", "входить в этот регион"); return false; }
        if (!service.canExit(player, target, position)) {
            if (teleport && service.allows(player.serverLevel(), player.blockPosition(), player, "exit-via-teleport")) return true;
            service.deny(player, player.serverLevel(), player.blockPosition(), "exit-deny-message", "покидать этот регион"); return false;
        }
        return true;
    }
    public void tick(ServerPlayer player) {
        Session state = sessions.computeIfAbsent(player.getUUID(), id -> new Session());
        List<Region> current = service.regions(player.serverLevel(), player.blockPosition());
        String dimension = player.serverLevel().dimension().location().toString();
        Set<String> ids = new LinkedHashSet<>(); current.forEach(r -> ids.add(r.id()));
        Object greeting = service.value(player.serverLevel(), player.blockPosition(), player, "greeting");
        Object greetingTitle = service.value(player.serverLevel(), player.blockPosition(), player, "greeting-title");
        Object farewell = service.value(player.serverLevel(), player.blockPosition(), player, "farewell");
        Object farewellTitle = service.value(player.serverLevel(), player.blockPosition(), player, "farewell-title");
        if (!Objects.equals(state.dimension, dimension) || !state.regions.equals(ids)) {
            Region previous = state.lastRegions.values().stream().findFirst().orElse(null);
            Region entered = current.stream().findFirst().orElse(null);
            if (previous != null) {
                sendFlag(player, previous, Objects.equals(state.farewell, farewell) ? null : state.farewell,
                    Objects.equals(state.farewellTitle, farewellTitle) ? null : state.farewellTitle);
                if (state.notifyLeave) notify(player, previous.id(), false);
            }
            if (entered != null) {
                sendFlag(player, entered, Objects.equals(state.greeting, greeting) ? null : greeting,
                    Objects.equals(state.greetingTitle, greetingTitle) ? null : greetingTitle);
                if (Boolean.TRUE.equals(service.value(player.serverLevel(), player.blockPosition(), player, "notify-enter"))) notify(player, entered.id(), true);
            }
            state.dimension = dimension; state.regions = ids;
            state.lastRegions.clear(); current.forEach(r -> state.lastRegions.put(r.id(), r));
        }
        state.greeting = greeting; state.greetingTitle = greetingTitle; state.farewell = farewell; state.farewellTitle = farewellTitle;
        state.notifyLeave = Boolean.TRUE.equals(service.value(player.serverLevel(), player.blockPosition(), player, "notify-leave"));
        updateGameMode(player, state);
        healAndFeed(player, state);
        if (player.tickCount % 20 == 0) updateWeatherAndTime(player);
    }
    private void sendFlag(ServerPlayer player, Region region, Object message, Object heading) {
        if (message != null && !message.toString().isEmpty()) player.sendSystemMessage(text(player, region, message.toString()));
        if (heading != null) {
            String[] parts = heading.toString().split("\\n", 2);
            if (service.config().bool(player.serverLevel().dimension().location().toString(),"regions.titles-always-use-default-times",true))
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(text(player, region, parts[0])));
            player.connection.send(new ClientboundSetSubtitleTextPacket(text(player, region, parts.length > 1 ? parts[1] : "")));
        }
    }
    private Component text(ServerPlayer player, Region region, String value) {
        return Component.literal(value.replace("%name%", player.getGameProfile().getName()).replace("%id%", region.id())
            .replace("%world%", player.serverLevel().dimension().location().toString()).replace('&', '§'));
    }
    private void notify(ServerPlayer player, String id, boolean entering) {
        Component message = Component.literal(player.getGameProfile().getName() + (entering ? " вошёл в " : " вышел из ") + id).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer recipient : service.server().getPlayerList().getPlayers())
            if (recipient.hasPermissions(service.config().operatorLevel())) recipient.sendSystemMessage(message);
    }
    private void updateGameMode(ServerPlayer player, Session state) {
        Object value = service.bypass(player) ? null : service.value(player.serverLevel(), player.blockPosition(), player, "game-mode");
        GameType mode = value == null ? null : GameType.byName(value.toString(), null);
        if (mode != null) {
            if (state.originalMode == null) state.originalMode = player.gameMode.getGameModeForPlayer() == mode
                    ? service.server().getDefaultGameType() : player.gameMode.getGameModeForPlayer();
            if (player.gameMode.getGameModeForPlayer() != mode) player.setGameMode(mode);
        } else restoreMode(player, state);
    }
    private void restoreMode(ServerPlayer player, Session state) {
        if (state.originalMode != null) { player.setGameMode(state.originalMode); state.originalMode = null; }
    }
    private double number(ServerPlayer player, String flag, double fallback) {
        Object value = service.value(player.serverLevel(), player.blockPosition(), player, flag);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
    private void healAndFeed(ServerPlayer player, Session state) {
        if (player.isDeadOrDying()) return;
        int tick = service.server().getTickCount();
        Object heal = service.value(player.serverLevel(), player.blockPosition(), player, "heal-amount");
        Object delay = service.value(player.serverLevel(), player.blockPosition(), player, "heal-delay");
        if (heal instanceof Number amount && delay instanceof Number interval && interval.intValue() >= 0
            && tick - state.lastHeal >= Math.max(1L, interval.longValue() * 20L)) {
            float min = (float) Math.clamp(number(player, "heal-min-health", 0), 0, player.getMaxHealth());
            float max = (float) Math.clamp(number(player, "heal-max-health", player.getMaxHealth()), min, player.getMaxHealth());
            if (amount.floatValue() > 0 || !invincible(player) && (player.gameMode.getGameModeForPlayer()==GameType.SURVIVAL || player.gameMode.getGameModeForPlayer()==GameType.ADVENTURE)) {
                if (amount.floatValue() != 0) player.setHealth(interval.intValue() == 0 ? (amount.floatValue() > 0 ? max : min)
                    : Math.clamp(player.getHealth() + amount.floatValue(), min, max));
                state.lastHeal = tick;
                if(player.getHealth()<=0 && !player.isRemoved()) player.die(player.damageSources().magic());
            }
        }
        Object feed = service.value(player.serverLevel(), player.blockPosition(), player, "feed-amount");
        Object feedDelay = service.value(player.serverLevel(), player.blockPosition(), player, "feed-delay");
        if (feed instanceof Number amount && feedDelay instanceof Number interval && interval.intValue() >= 0
            && tick - state.lastFeed >= Math.max(1L, interval.longValue() * 20L)) {
            int min = (int) Math.clamp(number(player, "feed-min-hunger", 0), 0, 20);
            int max = (int) Math.clamp(number(player, "feed-max-hunger", 20), min, 20);
            if (amount.intValue() > 0 || !invincible(player) && (player.gameMode.getGameModeForPlayer()==GameType.SURVIVAL || player.gameMode.getGameModeForPlayer()==GameType.ADVENTURE)) {
                if (amount.intValue() != 0) {
                    player.getFoodData().setFoodLevel(interval.intValue() == 0 ? (amount.intValue() > 0 ? max : min)
                        : Math.clamp((long)player.getFoodData().getFoodLevel() + amount.longValue(), min, max));
                    player.getFoodData().setSaturation(player.getFoodData().getFoodLevel());
                }
                state.lastFeed = tick;
            }
        }
    }
    public boolean invincible(ServerPlayer player) { return "allow".equals(service.value(player.serverLevel(), player.blockPosition(), player, "invincible")); }
    public Packet<?> transformPacket(ServerPlayer player, Packet<?> packet) {
        if(packet instanceof ClientboundSetDefaultSpawnPositionPacket spawn && service.compassTarget(player) != null)
            return new ClientboundSetDefaultSpawnPositionPacket(service.compassTarget(player),spawn.getAngle());
        if (packet instanceof ClientboundSetTimePacket) {
            Object value = service.value(player.serverLevel(), player.blockPosition(), player, "time-lock");
            if (value != null) {
                String text = value.toString();
                try {
                    boolean relative = text.startsWith("+") || text.startsWith("-");
                    long time = Long.parseLong(text);
                    return new ClientboundSetTimePacket(player.serverLevel().getGameTime(), relative ? player.serverLevel().getDayTime() + time : Math.floorMod(time, 24000), relative);
                } catch (NumberFormatException ignored) { /* Legacy unknown values preserved by import. */ }
            }
        }
        if (packet instanceof ClientboundGameEventPacket event) {
            Object value = service.value(player.serverLevel(), player.blockPosition(), player, "weather-lock");
            if (value != null) {
                boolean rain = !value.toString().equals("clear");
                boolean thunder = value.toString().equals("thunder");
                if (event.getEvent() == ClientboundGameEventPacket.START_RAINING || event.getEvent() == ClientboundGameEventPacket.STOP_RAINING)
                    return new ClientboundGameEventPacket(rain ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0);
                if (event.getEvent() == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE)
                    return new ClientboundGameEventPacket(event.getEvent(), rain ? 1 : 0);
                if (event.getEvent() == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE)
                    return new ClientboundGameEventPacket(event.getEvent(), thunder ? 1 : 0);
            }
        }
        return packet;
    }
    private void updateWeatherAndTime(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        player.connection.send(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
        player.connection.send(new ClientboundGameEventPacket(level.isRaining() ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0));
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.getRainLevel(1)));
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.getThunderLevel(1)));
    }
    public void respawn(ServerPlayer player) {
        Session state = sessions.get(player.getUUID());
        if (state == null) return;
        if (state.deathSpawn instanceof Map<?, ?> location) {
                String world = service.resolveWorld(String.valueOf(location.containsKey("world") ? location.get("world") : state.dimension));
                if (world.isBlank()) world = state.dimension;
                ServerLevel target = service.server().getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(world)));
                if (target != null && location.get("x") instanceof Number x && location.get("y") instanceof Number y && location.get("z") instanceof Number z) {
                    float yaw = location.get("yaw") instanceof Number n ? n.floatValue() : player.getYRot();
                    float pitch = location.get("pitch") instanceof Number n ? n.floatValue() : player.getXRot();
                    force(player, () -> player.teleportTo(target, x.doubleValue(), y.doubleValue(), z.doubleValue(), yaw, pitch));
                }
        }
        state.deathSpawn = null;
        state.regions = Set.of();
    }
    public void death(ServerPlayer player) {
        Session state = sessions.computeIfAbsent(player.getUUID(), id -> new Session());
        state.dimension = player.serverLevel().dimension().location().toString();
        state.deathSpawn = service.value(player.serverLevel(), player.blockPosition(), player, "spawn");
    }
    public void reload() { clear(); }
    public void clear() { restoreAll(); sessions.clear(); }
    public void restoreAll() {
        for (ServerPlayer player : service.server().getPlayerList().getPlayers()) {
            Session state = sessions.get(player.getUUID()); if (state != null) restoreMode(player, state);
        }
    }
    public void logout(ServerPlayer player) { Session state = sessions.remove(player.getUUID()); if (state != null) restoreMode(player, state); }
    private static final class Session {
        String dimension;
        Set<String> regions = Set.of();
        final Map<String, Region> lastRegions = new LinkedHashMap<>();
        GameType originalMode;
        Object greeting, greetingTitle, farewell, farewellTitle, deathSpawn;
        boolean notifyLeave;
        int lastHeal, lastFeed;
    }
}
