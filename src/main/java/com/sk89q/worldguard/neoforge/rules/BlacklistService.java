package com.sk89q.worldguard.neoforge.rules;

import com.sk89q.worldguard.neoforge.ProtectionService;
import com.sk89q.worldguard.neoforge.WorldGuardConfig;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.sk89q.worldguard.neoforge.storage.JdbcSettings;
import com.sk89q.worldguard.neoforge.storage.SqlBlacklistLogger;

/** Native execution of legacy blacklist actions. Uses operator levels for notifications and bypass. */
public final class BlacklistService {
    private final ProtectionService service;
    private final Map<String, List<BlacklistRules>> worlds = new HashMap<>();
    private final Map<String, Long> repeated = new LinkedHashMap<>();
    private BlacklistRules global;
    private final Map<String, SqlBlacklistLogger> databaseLogs = new HashMap<>();

    public BlacklistService(ProtectionService service) throws IOException {
        this.service = service;
        reload();
    }
    public void reload() throws IOException {
        BlacklistRules next = BlacklistRules.load(service.directory().resolve("blacklist.txt"));
        Map<String, List<BlacklistRules>> loaded = new HashMap<>();
        for (ServerLevel level : service.server().getAllLevels()) {
            String dimension = level.dimension().location().toString();
            loaded.put(dimension, loadWorld(next, dimension));
        }
        global = next; worlds.clear(); worlds.putAll(loaded); repeated.clear(); databaseLogs.clear();
    }
    private List<BlacklistRules> loadWorld(BlacklistRules root, String dimension) throws IOException {
        Path path = WorldGuardConfig.worldDirectory(service.directory().resolve("worlds"), dimension).resolve("blacklist.txt");
        BlacklistRules local = BlacklistRules.load(path);
        List<BlacklistRules> rules = new ArrayList<>(); rules.add(root); rules.add(local);
        for (BlacklistRules list : rules) for (BlacklistRules.Rule rule : list.rules()) {
            if (!rule.ignoreGroups().isEmpty() || !rule.ignorePermissions().isEmpty()) {
                WorldGuardMod.LOGGER.info("Blacklist {} uses native group/permission providers and vanilla operator levels", dimension);
                break;
            }
        }
        return List.copyOf(rules);
    }
    public boolean check(ServerLevel level, BlockPos pos, ServerPlayer player, String event, ItemStack item) {
        return item.isEmpty() || checkId(level, pos, player, event, BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
    }
    public boolean checkBlock(ServerLevel level, BlockPos pos, ServerPlayer player, String event, BlockState block) {
        return checkId(level, pos, player, event, BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString());
    }
    private boolean checkId(ServerLevel level, BlockPos pos, ServerPlayer player, String event, String id) {
        if (!service.healthy()) return false;
        if (player != null && service.bypass(player)) return true;
        String dimension = level.dimension().location().toString();
        List<BlacklistRules> lists = worlds.get(dimension);
        if (lists == null) {
            try { lists = loadWorld(global, dimension); worlds.put(dimension, lists); }
            catch (IOException exception) {
                WorldGuardMod.LOGGER.error("Cannot load blacklist for {}; refusing the action", dimension, exception);
                return false;
            }
        }
        boolean whitelist = service.config().bool(dimension, "blacklist.use-as-whitelist", false), allowed = true;
        for (BlacklistRules list : lists) for (BlacklistRules.Rule rule : list.matching(id)) {
            if (player != null && (rule.ignoreGroups().stream().anyMatch(group -> service.inGroup(player,group))
                    || rule.ignorePermissions().stream().anyMatch(permission -> nativePermission(player,permission)))) continue;
            allowed &= rule.allows(event, whitelist);
            List<String> actions = rule.actions(event);
            if (actions.isEmpty()) continue;
            long tick = service.server().getTickCount();
            String key = dimension + "/" + (player == null ? pos.asLong() : player.getUUID()) + "/" + event + "/" + id;
            boolean repeat = tick - repeated.getOrDefault(key, -1000L) < 100;
            if (!repeat) {
                if (repeated.size() > 2048) repeated.clear();
                repeated.put(key, tick);
            }
            String message = rule.message() == null ? "WorldGuard: " + event.substring(3) + " " + id + " is not allowed here."
                    : rule.message().replace("%s", id).replace('&', '§');
            String description = (player == null ? "dispenser" : player.getGameProfile().getName()) + " " + event + " " + id
                    + " at " + dimension + " " + pos.toShortString() + (rule.comment() == null ? "" : ": " + rule.comment());
            for (String action : actions) switch (action) {
                case "tell" -> { if (!repeat && player != null) player.sendSystemMessage(Component.literal(message)); }
                case "notify" -> {
                    if (!repeat) for (ServerPlayer admin : service.server().getPlayerList().getPlayers())
                        if (admin.hasPermissions(service.config().operatorLevel())) admin.sendSystemMessage(Component.literal("[WorldGuard] " + description));
                }
                case "log" -> { if (!repeat) {
                    log(dimension, description);
                    logDatabase(dimension, event, player, pos, id, rule.comment());
                } }
                case "kick" -> { if (player != null) { player.connection.disconnect(Component.literal(message)); allowed = false; } }
                case "ban" -> {
                    if (player != null) {
                        service.server().getPlayerList().getBans().add(new UserBanListEntry(player.getGameProfile(), null, "WorldGuard", null, message));
                        player.connection.disconnect(Component.literal(message)); allowed = false;
                    }
                }
                default -> { }
            }
        }
        return allowed;
    }
    private void log(String dimension, String description) {
        if (service.config().bool(dimension, "blacklist.logging.console.enable", true)) WorldGuardMod.LOGGER.info("[Blacklist] {}", description);
        if (!service.config().bool(dimension, "blacklist.logging.file.enable", false)) return;
        LocalDate date = LocalDate.now();
        String pattern = service.config().string(dimension, "blacklist.logging.file.path", "worldguard/logs/%Y-%m-%d.log")
                .replace("%Y", Integer.toString(date.getYear())).replace("%m", String.format("%02d", date.getMonthValue()))
                .replace("%d", String.format("%02d", date.getDayOfMonth()));
        Path path = service.server().getServerDirectory().resolve(pattern).normalize();
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, "[" + java.time.Instant.now() + "] " + description + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) { WorldGuardMod.LOGGER.error("Unable to write blacklist log {}", path, exception); }
    }
    private void logDatabase(String dimension, String event, ServerPlayer player, BlockPos position, String item, String comment) {
        if (!service.config().bool(dimension, "blacklist.logging.database.enable", false)) return;
        try {
            SqlBlacklistLogger logger = databaseLogs.computeIfAbsent(dimension, world -> new SqlBlacklistLogger(new JdbcSettings(
                    service.config().string(world,"blacklist.logging.database.dsn","jdbc:mysql://localhost:3306/minecraft"),
                    service.config().string(world,"blacklist.logging.database.user","root"),
                    service.config().string(world,"blacklist.logging.database.pass",""), ""),
                    service.config().string(world,"blacklist.logging.database.table","worldguard_blacklist")));
            logger.log(event, dimension, player == null ? null : player.getGameProfile().getName(), position.getX(),position.getY(),position.getZ(),item,comment);
        } catch (java.sql.SQLException | RuntimeException e) { WorldGuardMod.LOGGER.error("Unable to write blacklist SQL log for {}", dimension, e); }
    }
    private boolean nativePermission(ServerPlayer player, String permission) {
        return service.hasPermission(player,permission);
    }
}
