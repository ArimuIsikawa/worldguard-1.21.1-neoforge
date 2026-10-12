package com.sk89q.worldguard.neoforge;

import com.sk89q.worldguard.neoforge.region.*;
import com.sk89q.worldguard.neoforge.sessions.PlayerSessions;
import com.sk89q.worldguard.neoforge.rules.BlacklistService;
import com.sk89q.worldguard.neoforge.storage.SqlRegionStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Server-owned facade. No client registrations or custom network payloads. */
public final class ProtectionService {
    private final MinecraftServer server;
    private final Path directory;
    private final RegionRepository repository;
    private WorldGuardConfig config;
    private final Set<UUID> bypassOverrides = new HashSet<>();
    private final Set<UUID> gods = new HashSet<>();
    private final Set<String> fireDisabled = new HashSet<>();
    private boolean halted;
    private final Map<String, CandidateCache> candidates = new HashMap<>();
    private final Map<UUID, Long> deniedMessages = new HashMap<>();
    private final PlayerSessions sessions;
    private final BlacklistService blacklist;
    private boolean healthy = true;
    private final Map<UUID, GroupCache> groupCache = new HashMap<>();
    private record GroupCache(long tick, Map<String,Boolean> groups) { }
    private record HopperPosition(String world, BlockPos pos) { }
    private final Set<HopperPosition> deniedHoppers = new HashSet<>();
    private final Map<UUID,BlockPos> compassTargets = new HashMap<>();
    private final com.sk89q.worldguard.neoforge.admin.PerformanceProfiler profiler;
    private final java.util.concurrent.atomic.AtomicInteger uuidLookups=new java.util.concurrent.atomic.AtomicInteger();
    public int uuidLookups() { return uuidLookups.get(); }
    public com.sk89q.worldguard.neoforge.admin.PerformanceProfiler profiler() { return profiler; }
    public BlockPos compassTarget(ServerPlayer player) { return compassTargets.get(player.getUUID()); }
    public void compassTarget(ServerPlayer player, BlockPos target) {
        if(target == null) compassTargets.remove(player.getUUID()); else compassTargets.put(player.getUUID(),target.immutable());
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket(target == null ? player.serverLevel().getSharedSpawnPos() : target,player.serverLevel().getSharedSpawnAngle()));
    }
    public void hopperDenied(ServerLevel level, BlockPos pos) {
        if (config.bool(level.dimension().location().toString(), "event-handling.break-hoppers-on-denied-move", true))
            deniedHoppers.add(new HopperPosition(level.dimension().location().toString(),pos.immutable()));
    }
    public void processDeniedHoppers() {
        List<HopperPosition> pending = List.copyOf(deniedHoppers); deniedHoppers.clear();
        for (HopperPosition hopper : pending) {
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(hopper.world)));
            if (level != null && level.hasChunkAt(hopper.pos) && level.getBlockState(hopper.pos).getBlock() instanceof net.minecraft.world.level.block.HopperBlock)
                level.destroyBlock(hopper.pos,true);
        }
    }
    public boolean inGroup(ServerPlayer player, String group) {
        GroupCache cache = groupCache.get(player.getUUID());
        long tick = server.getTickCount();
        if (cache == null || tick-cache.tick >= 20 || config.bool(null,"disable-permission-cache",false)) {
            cache = new GroupCache(tick,new HashMap<>()); groupCache.put(player.getUUID(),cache);
        }
        return cache.groups.computeIfAbsent(group.toLowerCase(Locale.ROOT), name -> {
            boolean operator = (name.equals("op") || name.equals("operators")) && player.hasPermissions(config.operatorLevel());
            var event = new com.sk89q.worldguard.neoforge.api.RegionGroupEvent(player,name,operator);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event); return event.isMember();
        });
    }
    public int maximumClaims(ServerPlayer player) {
        return maximumClaims(player, player.serverLevel().dimension().location().toString());
    }
    public int maximumClaims(ServerPlayer player, String world) {
        int limit = config.integer(world,"regions.max-region-count-per-player.default",7);
        Object limits = config.value(world,"regions.max-region-count-per-player");
        if (limits instanceof Map<?,?> values) for (var value : values.entrySet()) {
            if (!value.getKey().toString().equals("default") && value.getValue() instanceof Number count && inGroup(player,value.getKey().toString())) {
                if (count.intValue() < 0) return -1;
                limit = Math.max(limit,count.intValue());
            }
        }
        return limit;
    }
    public boolean hasPermission(ServerPlayer player, String permission) {
        boolean allowed = player.hasPermissions(config.operatorLevel()) && config.bool(null,"op-permissions",true);
        if (permission.startsWith("operator-level.")) try { allowed=player.hasPermissions(Integer.parseInt(permission.substring("operator-level.".length()))); }
        catch (NumberFormatException e) { allowed=false; }
        var event=new com.sk89q.worldguard.neoforge.api.RegionPermissionEvent(player,permission,allowed);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event); return event.isAllowed();
    }
    public boolean buildPermission(ServerLevel level, Entity actor, String kind, String id, String action) {
        if (!config.bool(level.dimension().location().toString(),"build-permission-nodes.enable",false) || !(actor(actor) instanceof ServerPlayer player)) return true;
        boolean allowed=hasPermission(player,"worldguard.build." + kind + "." + id + "." + action)
                || hasPermission(player,"worldguard.build." + kind + "." + action + "." + id);
        if(!allowed) {
            String message=config.string(level.dimension().location().toString(),"build-permission-nodes.deny-message","&eSorry, but you are not permitted to do that here.");
            if(!message.isEmpty()) player.displayClientMessage(Component.literal(message.replace('&','\u00a7')),true);
        }
        return allowed;
    }

    public ProtectionService(MinecraftServer server, Path directory) throws IOException {
        this.server = server;
        this.directory = directory;
        profiler=new com.sk89q.worldguard.neoforge.admin.PerformanceProfiler(directory.resolve("profiles"));
        Files.createDirectories(directory.resolve("import"));
        Path legacy = server.getServerDirectory().resolve("plugins/WorldGuard");
        Path configFile = directory.resolve("config.yml");
        if (!Files.exists(configFile)) {
            if (Files.isRegularFile(legacy.resolve("config.yml"))) Files.copy(legacy.resolve("config.yml"), configFile);
            else try (var input = ProtectionService.class.getResourceAsStream("/defaults/config.yml")) {
                if (input == null) throw new IOException("Missing default configuration");
                Files.copy(input, configFile);
            }
        }
        config = new WorldGuardConfig(directory);
        repository = new RegionRepository(directory.resolve("worlds"), sqlStorage(config));
        if (config.bool(null, "migration.auto-import", true)) migrate(legacy);
        for (ServerLevel level : server.getAllLevels()) load(level.dimension().location().toString());
        blacklist = new BlacklistService(this);
        sessions = new PlayerSessions(this);
    }

    private void migrate(Path legacy) throws IOException {
        Map<String, String> mapping = config.migrationWorlds();
        if (mapping.isEmpty()) mapping = Map.of("world", "minecraft:overworld", "world_nether", "minecraft:the_nether", "world_the_end", "minecraft:the_end");
        Set<String> used = new HashSet<>();
        for (var entry : mapping.entrySet()) {
            if (!used.add(entry.getValue())) throw new IOException("Multiple legacy worlds map to " + entry.getValue());
            Path source = legacy.resolve("worlds").resolve(entry.getKey()).normalize();
            if (!source.startsWith(legacy.resolve("worlds").normalize())) throw new IOException("Invalid legacy world name");
            Path target = WorldGuardConfig.worldDirectory(directory.resolve("worlds"), entry.getValue());
            if (Files.isRegularFile(source.resolve("regions.yml")) && (repository.isSql()
                    ? repository.regions(entry.getValue()).isEmpty() : !Files.exists(target.resolve("regions.yml")))) {
                int count = repository.importYaml(source.resolve("regions.yml"), entry.getValue(), false);
                WorldGuardMod.LOGGER.info("Imported {} regions from {} into {}", count, entry.getKey(), entry.getValue());
            }
            Files.createDirectories(target);
            for (String file : List.of("config.yml", "blacklist.txt"))
                if (Files.isRegularFile(source.resolve(file)) && !Files.exists(target.resolve(file))) Files.copy(source.resolve(file), target.resolve(file));
        }
        if (Files.isRegularFile(legacy.resolve("blacklist.txt")) && !Files.exists(directory.resolve("blacklist.txt")))
            Files.copy(legacy.resolve("blacklist.txt"), directory.resolve("blacklist.txt"));
    }

    private void load(String world) throws IOException { config.loadWorld(world); repository.regions(world); }
    public MinecraftServer server() { return server; }
    public Path directory() { return directory; }
    public RegionRepository repository() { return repository; }
    public WorldGuardConfig config() { return config; }
    public PlayerSessions sessions() { return sessions; }
    public BlacklistService blacklist() { return blacklist; }
    public boolean healthy() { return healthy; }
    public boolean halted() { return halted; }
    public void halted(boolean value) { halted = value; }
    public void fireDisabled(String world, boolean value) { if (value) fireDisabled.add(world); else fireDisabled.remove(world); }
    public void god(ServerPlayer player, boolean value) { if (value) gods.add(player.getUUID()); else gods.remove(player.getUUID()); }
    public boolean god(ServerPlayer player) { return gods.contains(player.getUUID()); }
    private final Set<UUID> amphibious = new HashSet<>();
    public void waterBreathing(ServerPlayer player, boolean value) { if (value) amphibious.add(player.getUUID()); else amphibious.remove(player.getUUID()); }
    public boolean waterBreathing(ServerPlayer player) { return amphibious.contains(player.getUUID()); }

    public void reload() throws IOException {
        try {
            WorldGuardConfig next = new WorldGuardConfig(directory);
            for (ServerLevel level : server.getAllLevels()) next.loadWorld(level.dimension().location().toString());
            SqlRegionStorage nextSql = sqlStorage(next);
            Set<String> dimensions = new HashSet<>(repository.allDimensions());
            if (nextSql != null) dimensions.addAll(nextSql.dimensions());
            repository.reloadAll(dimensions, nextSql);
            config = next;
            candidates.clear();
            groupCache.clear();
            healthy = true;
            blacklist.reload();
            sessions.reload();
        } catch (IOException | RuntimeException e) {
            healthy = false;
            if (e instanceof IOException io) throw io;
            throw new IOException("Unable to reload WorldGuard", e);
        }
    }
    public void importConfiguration(Path source, String dimension) throws IOException {
        WorldGuardConfig.read(source); // Parse completely before replacing a configuration.
        Path target = dimension == null ? directory.resolve("config.yml")
            : WorldGuardConfig.worldDirectory(directory.resolve("worlds"), dimension).resolve("config.yml");
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) Files.copy(target, target.resolveSibling("config.yml.bak"), StandardCopyOption.REPLACE_EXISTING);
        boolean existed = Files.exists(target);
        Path staged = Files.createTempFile(target.getParent(), "config-", ".tmp");
        try {
            Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING); }
            try { reload(); }
            catch (IOException e) {
                if (existed) Files.copy(target.resolveSibling("config.yml.bak"), target, StandardCopyOption.REPLACE_EXISTING);
                else Files.deleteIfExists(target);
                try { reload(); } catch (IOException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } finally { Files.deleteIfExists(staged); }
    }
    public void flushSessions() { sessions.clear(); }
    public void beginUuidMigration() throws IOException {
        beginUuidMigration(false);
    }
    public void beginUuidMigration(boolean force) throws IOException {
        if ((!force && !config.bool(null,"regions.uuid-migration.perform-on-next-start",true)) || server.getProfileCache() == null) return;
        if(uuidLookups.get()>0) throw new IllegalStateException("UUID migration is already running");
        Set<String> dimensions=new HashSet<>(repository.storedDimensions()); dimensions.addAll(repository.allDimensions());
        Set<String> names=new LinkedHashSet<>();
        for(String dimension : dimensions) for(Region region : repository.regions(dimension).values()) {
            region.owners().players().forEach(name->names.add(name.toLowerCase(Locale.ROOT)));
            region.members().players().forEach(name->names.add(name.toLowerCase(Locale.ROOT)));
        }
        if (names.isEmpty()) { config.setGlobal("regions.uuid-migration.perform-on-next-start",false); return; }
        Map<String,UUID> profiles=new java.util.concurrent.ConcurrentHashMap<>();
        Map<String,UUID> cachedProfiles=new HashMap<>();
        for(ServerPlayer player : server.getPlayerList().getPlayers()) cachedProfiles.put(player.getGameProfile().getName().toLowerCase(Locale.ROOT),player.getUUID());
        Path cacheFile=server.getServerDirectory().resolve("usercache.json");
        if(Files.isRegularFile(cacheFile)) try(var reader=Files.newBufferedReader(cacheFile)) {
            for(var entry : com.google.gson.JsonParser.parseReader(reader).getAsJsonArray()) {
                var profile=entry.getAsJsonObject();
                if(profile.has("name") && profile.has("uuid")) cachedProfiles.put(profile.get("name").getAsString().toLowerCase(Locale.ROOT),UUID.fromString(profile.get("uuid").getAsString()));
            }
        } catch(IOException | RuntimeException error) { WorldGuardMod.LOGGER.warn("Vanilla profile cache could not be read; unresolved legacy names remain available for migration"); }
        java.util.concurrent.atomic.AtomicInteger pending=new java.util.concurrent.atomic.AtomicInteger(names.size());
        uuidLookups.set(names.size());
        java.util.concurrent.atomic.AtomicBoolean lookupFailed=new java.util.concurrent.atomic.AtomicBoolean();
        Map<String,java.util.concurrent.CompletableFuture<Optional<com.mojang.authlib.GameProfile>>> unresolved=new LinkedHashMap<>();
        for(String name : names) {
            java.util.concurrent.CompletableFuture<Optional<com.mojang.authlib.GameProfile>> lookup;
            if(cachedProfiles.containsKey(name)) lookup=java.util.concurrent.CompletableFuture.completedFuture(Optional.of(new com.mojang.authlib.GameProfile(cachedProfiles.get(name),name)));
            else if(!server.usesAuthentication()) lookup=java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
            else { lookup=new java.util.concurrent.CompletableFuture<>(); unresolved.put(name,lookup); }
            lookup.exceptionally(error->{
            lookupFailed.set(true); return Optional.empty();
        }).thenAccept(profile -> {
            profile.ifPresent(p->profiles.put(name,p.getId()));
            uuidLookups.decrementAndGet();
            if(pending.decrementAndGet()==0) server.execute(()->{
                if(WorldGuardMod.service()!=this) return;
                if(lookupFailed.get()) { WorldGuardMod.LOGGER.warn("Startup UUID lookup failed; legacy names are retained and migration remains pending"); return; }
                try {
                    int count=0;
                    Path backup=directory.resolve("backups/uuid-" + java.time.Instant.now().toEpochMilli());
                    for(String dimension : dimensions) {
                        repository.exportYaml(dimension,WorldGuardConfig.worldDirectory(backup,dimension).resolve("regions.yml"));
                        count+=ProfileMigration.apply(repository.regions(dimension).values(),profiles,config.bool(null,"regions.uuid-migration.keep-names-that-lack-uuids",true),names);
                        repository.save(dimension);
                    }
                    config.setGlobal("regions.uuid-migration.perform-on-next-start",false);
                    WorldGuardMod.LOGGER.info("WorldGuard startup UUID migration completed: {} names converted or removed",count);
                } catch(IOException e) { healthy=false; WorldGuardMod.LOGGER.error("UUID migration could not save; protection is refusing actions until a successful reload",e); }
            });
        });
        }
        if(!unresolved.isEmpty()) java.util.concurrent.CompletableFuture.runAsync(()->{
            server.getProfileRepository().findProfilesByNames(unresolved.keySet().toArray(String[]::new),new com.mojang.authlib.ProfileLookupCallback() {
                @Override public void onProfileLookupSucceeded(com.mojang.authlib.GameProfile profile) {
                    var lookup=unresolved.get(profile.getName().toLowerCase(Locale.ROOT));
                    if(lookup!=null) { server.getProfileCache().add(profile); lookup.complete(Optional.of(profile)); }
                }
                @Override public void onProfileLookupFailed(String name,Exception exception) {
                    var lookup=unresolved.get(name.toLowerCase(Locale.ROOT));
                    if(lookup!=null) {
                        if(exception instanceof com.mojang.authlib.yggdrasil.ProfileNotFoundException) lookup.complete(Optional.empty());
                        else lookup.completeExceptionally(exception);
                    }
                }
            });
        },net.minecraft.Util.backgroundExecutor()).whenComplete((ignored,error)->{
            for(var lookup : unresolved.values()) if(!lookup.isDone()) lookup.completeExceptionally(error==null ? new IOException("Profile service did not return every requested name") : error);
        });
    }
    private static SqlRegionStorage sqlStorage(WorldGuardConfig config) throws IOException {
        return config.bool(null, "regions.sql.use", false) ? new SqlRegionStorage(config.sqlSettings(), config.worldAliases()) : null;
    }
    public int migrateDatabase(String from, String to) throws IOException {
        if (!Set.of("yaml", "sql").contains(from) || !Set.of("yaml", "sql").contains(to) || from.equals(to))
            throw new IllegalArgumentException("Use /rg migratedb yaml sql or /rg migratedb sql yaml");
        SqlRegionStorage database = new SqlRegionStorage(config.sqlSettings(), config.worldAliases());
        RegionRepository source = new RegionRepository(directory.resolve("worlds"), from.equals("sql") ? database : null);
        RegionRepository target = new RegionRepository(directory.resolve("worlds"), to.equals("sql") ? database : null);
        Path backup = directory.resolve("backups").resolve("migration-" + java.time.Instant.now().toEpochMilli());
        Map<String, Map<String, Region>> staged = new LinkedHashMap<>();
        for (String dimension : source.storedDimensions()) staged.put(dimension, new LinkedHashMap<>(source.regions(dimension)));
        for (String dimension : staged.keySet()) {
            Path backupFile = WorldGuardConfig.worldDirectory(backup, dimension).resolve("regions.yml");
            target.exportYaml(dimension, backupFile);
        }
        int count = 0;
        for (var entry : staged.entrySet()) { target.replace(entry.getKey(), entry.getValue()); count += entry.getValue().size(); }
        if (repository.isSql() == to.equals("sql")) reload();
        return count;
    }
    public void shutdown() throws IOException { profiler.close(); sessions.restoreAll(); if (healthy) repository.saveAll(); }
    public void logout(ServerPlayer player) {
        bypassOverrides.remove(player.getUUID()); gods.remove(player.getUUID()); deniedMessages.remove(player.getUUID()); sessions.logout(player);
        groupCache.remove(player.getUUID());
        amphibious.remove(player.getUUID());
        compassTargets.remove(player.getUUID());
        com.sk89q.worldguard.neoforge.command.RegionCommands.logout(player.getUUID());
    }

    public boolean bypass(Entity actor) {
        if (!(actor instanceof ServerPlayer player)) return false;
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer
                && config.bool(player.serverLevel().dimension().location().toString(),"regions.fake-player-build-override",true)) return true;
        boolean op = player.hasPermissions(config.operatorLevel());
        return op && (config.defaultBypass() != bypassOverrides.contains(player.getUUID()));
    }
    public boolean toggleBypass(ServerPlayer player) {
        if (!bypassOverrides.remove(player.getUUID())) bypassOverrides.add(player.getUUID());
        return bypass(player);
    }
    public void rememberOrigin(Entity entity) {
        var data=entity.getPersistentData(); String world=entity.level().dimension().location().toString();
        var previous=data.getCompound("worldguard_origin");
        if(world.equals(previous.getString("world"))) return;
        var origin=new net.minecraft.nbt.CompoundTag(); origin.putString("world",world);
        origin.putInt("x",entity.getBlockX()); origin.putInt("y",entity.getBlockY()); origin.putInt("z",entity.getBlockZ()); data.put("worldguard_origin",origin);
    }
    public BlockPos origin(Entity entity) {
        if(!config.bool(entity.level().dimension().location().toString(),"regions.use-paper-entity-origin",false)) return entity.blockPosition();
        var origin=entity.getPersistentData().getCompound("worldguard_origin");
        return entity.level().dimension().location().toString().equals(origin.getString("world")) ? new BlockPos(origin.getInt("x"),origin.getInt("y"),origin.getInt("z")) : entity.blockPosition();
    }
    public String resolveWorld(String name) {
        String mapped = config.migrationWorlds().getOrDefault(name, name);
        return switch (mapped) {
            case "world" -> "minecraft:overworld";
            case "world_nether" -> "minecraft:the_nether";
            case "world_the_end" -> "minecraft:the_end";
            default -> mapped;
        };
    }
    /** Native counterpart of WorldGuard's RegionOverlapAssociation for automation. */
    public boolean canAutomate(ServerLevel level, BlockPos source, BlockPos destination, String flag, boolean maxPriority) {
        return canAutomateUses(level, source, destination, maxPriority, flag);
    }
    public boolean canAutomateUses(ServerLevel level, BlockPos source, BlockPos destination, boolean maxPriority, String... flags) {
        if (!healthy) return false;
        String dimension = level.dimension().location().toString();
        for (String flag : flags) if (config.denied(dimension, flag)) return false;
        if (!config.regionsEnabled(dimension)) return true;
        List<Region> origins = regions(level, source).stream().filter(r -> !r.type().equals("global")).toList();
        if (maxPriority || config.bool(dimension, "protection.use-max-priority-association", false)) {
            int maximum = origins.stream().mapToInt(Region::priority).max().orElse(Integer.MIN_VALUE);
            origins = origins.stream().filter(r -> r.priority() == maximum).toList();
        }
        Set<String> ids = new HashSet<>(); origins.forEach(r -> ids.add(r.id()));
        Set<String> domains = new HashSet<>();
        try {
            Map<String, Region> all = repository.regions(dimension);
            for (Region r : origins) for (Region p = r; p != null; p = p.parent() == null ? null : all.get(p.parent())) {
                Object value = p.flags().get("nonplayer-protection-domains");
                if (value instanceof Collection<?> c) { c.forEach(v -> domains.add(v.toString())); break; }
            }
            boolean emptyOrigin = origins.isEmpty();
            RegionQuery query = new RegionQuery(all.values(), null, null, target -> {
                if (target.type().equals("global") && emptyOrigin || ids.contains(target.id())) return true;
                for (Region p = target; p != null; p = p.parent() == null ? null : all.get(p.parent())) {
                    Object value = p.flags().get("nonplayer-protection-domains");
                    if (value instanceof Collection<?> c) { return c.stream().anyMatch(v -> domains.contains(v.toString())); }
                }
                return false;
            });
            return flags.length == 1 && flags[0].equals("build") ? query.canBuild(destination.getX(), destination.getY(), destination.getZ())
                : query.canUse(destination.getX(), destination.getY(), destination.getZ(), flags);
        } catch (IOException e) { healthy = false; return false; }
    }
    public Entity actor(Entity entity) {
        Entity current = entity;
        Set<UUID> seen = new HashSet<>();
        while (current != null && seen.add(current.getUUID())) {
            if (current instanceof Projectile p && p.getOwner() != null) current = p.getOwner();
            else if (current instanceof OwnableEntity o && o.getOwner() != null) current = o.getOwner();
            else return current;
        }
        return current;
    }

    private RegionQuery query(ServerLevel level, BlockPos pos, Entity actor) {
        String dimension = level.dimension().location().toString();
        Collection<Region> regions;
        try {
            Map<String, Region> all = repository.regions(dimension);
            long revision = repository.revision(dimension);
            CandidateCache cache = candidates.get(dimension);
            if (cache == null || cache.revision != revision) {
                cache = new CandidateCache(revision); candidates.put(dimension, cache);
            }
            int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
            long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
            regions = cache.chunks.get(key);
            if (regions == null) {
                List<Region> selected = new ArrayList<>();
                // Include parents outside the chunk so inheritance can be resolved.
                Set<String> ids = new HashSet<>();
                for (Region r : all.values()) {
                    if (r.type().equals("global") || r.minX() <= ((cx << 4) + 15) && r.maxX() >= (cx << 4)
                        && r.minZ() <= ((cz << 4) + 15) && r.maxZ() >= (cz << 4)) {
                        for (Region p = r; p != null && ids.add(p.id()); p = p.parent() == null ? null : all.get(p.parent())) selected.add(p);
                    }
                }
                if (cache.chunks.size() > 8192) cache.chunks.clear();
                cache.chunks.put(key, selected); regions = selected;
            }
        } catch (IOException e) {
            healthy = false;
            throw new IllegalStateException("Cannot load protection for " + dimension + "; refusing unprotected operation", e);
        }
        Entity resolved = actor(actor);
        RegionQuery result = new RegionQuery(regions, resolved instanceof ServerPlayer p ? p.getUUID() : null,
            resolved instanceof ServerPlayer p ? p.getGameProfile().getName() : null);
        if (resolved instanceof ServerPlayer player) result.withGroups(group -> inGroup(player,group));
        return result;
    }
    public List<Region> regions(ServerLevel level, BlockPos pos) { return query(level, pos, null).applicable(pos.getX(), pos.getY(), pos.getZ()); }
    public Object value(ServerLevel level, BlockPos pos, Entity actor, String flag) {
        Object result = !config.regionsEnabled(level.dimension().location().toString()) ? FlagRegistry.defaultValue(flag)
                : query(level, pos, actor).value(pos.getX(), pos.getY(), pos.getZ(), flag);
        if (flag.equals("invincible") && actor instanceof ServerPlayer player && god(player)
                && (!"deny".equals(result) || hasPermission(player,"worldguard.god.override-regions"))) return "allow";
        return result;
    }
    public Object explicitValue(ServerLevel level, BlockPos pos, Entity actor, String flag) { return query(level, pos, actor).explicitValue(pos.getX(), pos.getY(), pos.getZ(), flag); }
    public Set<String> setValues(ServerLevel level, BlockPos pos, Entity actor, String flag) {
        Set<String> result = new LinkedHashSet<>();
        for (Object value : query(level, pos, actor).values(pos.getX(), pos.getY(), pos.getZ(), flag))
            if (value instanceof Collection<?> c) c.forEach(v -> result.add(v.toString()));
        return result;
    }
    public boolean allows(ServerLevel level, BlockPos pos, Entity actor, String flag) {
        return allows(level,pos,actor,flag,true);
    }
    /** Direct flag queries for effects and chat do not use the building bypass. */
    public boolean testState(ServerLevel level, BlockPos pos, Entity actor, String flag) {
        return allows(level,pos,actor,flag,false);
    }
    private boolean configuredDenial(ServerLevel level,Entity entity,String flag) {
        if(!config.denied(level.dimension().location().toString(),flag)) return false;
        return !flag.equals("lighter") || !(actor(entity) instanceof ServerPlayer player) || !hasPermission(player,"worldguard.override.lighter");
    }
    private boolean allows(ServerLevel level, BlockPos pos, Entity actor, String flag, boolean useBypass) {
        if (halted && (flag.equals("mob-spawning") || flag.equals("water-flow") || flag.equals("lava-flow") || flag.equals("fire-spread"))) return false;
        if ((flag.equals("fire-spread") || flag.equals("lava-fire")) && fireDisabled.contains(level.dimension().location().toString())) return false;
        if (configuredDenial(level,actor,flag)) return false;
        if (!healthy) return false;
        if (Set.of("fire-spread","lava-fire","water-flow","lava-flow").contains(flag)
                && !config.bool(level.dimension().location().toString(),"regions.high-frequency-flags",true)) return true;
        Entity resolved = actor(actor);
        if (useBypass && bypass(resolved) && (!flag.equals("pvp") || resolved instanceof net.neoforged.neoforge.common.util.FakePlayer)
                || !config.regionsEnabled(level.dimension().location().toString())) return true;
        return query(level, pos, actor).allows(pos.getX(), pos.getY(), pos.getZ(), flag);
    }
    public boolean canBuild(ServerLevel level, BlockPos pos, Entity actor) {
        if (!healthy) return false;
        if (bypass(actor(actor)) || !config.regionsEnabled(level.dimension().location().toString())) return true;
        return query(level, pos, actor).canBuild(pos.getX(), pos.getY(), pos.getZ());
    }
    public boolean canUse(ServerLevel level, BlockPos pos, Entity actor, String... flags) {
        if (!healthy) return false;
        for (String flag : flags) if (configuredDenial(level,actor,flag)) return false;
        if (bypass(actor(actor)) || !config.regionsEnabled(level.dimension().location().toString())) return true;
        return query(level, pos, actor).canUse(pos.getX(), pos.getY(), pos.getZ(), flags);
    }
    public boolean canEnter(ServerPlayer player, ServerLevel target, BlockPos pos) {
        if (!healthy) return false;
        if (bypass(player) || !config.regionsEnabled(target.dimension().location().toString())) return true;
        Set<String> old = new HashSet<>();
        if (player.serverLevel() == target) regions(target, player.blockPosition()).forEach(r -> old.add(r.id()));
        boolean entering = regions(target, pos).stream().anyMatch(r -> !old.contains(r.id()));
        return !entering || allows(target, pos, player, "entry");
    }
    public boolean canExit(ServerPlayer player, ServerLevel target, BlockPos pos) {
        if (!healthy) return false;
        if (bypass(player)) return true;
        if (Boolean.TRUE.equals(value(target, pos, player, "exit-override"))) return true;
        Set<String> targetIds = new HashSet<>();
        if (player.serverLevel() == target) regions(target, pos).forEach(r -> targetIds.add(r.id()));
        List<Region> old = regions(player.serverLevel(), player.blockPosition());
        for (Region region : old) if (!targetIds.contains(region.id())) {
            RegionQuery q = query(player.serverLevel(), player.blockPosition(), player);
            if (!q.allows(player.getBlockX(), player.getBlockY(), player.getBlockZ(), "exit")) return false;
        }
        return true;
    }
    public void deny(ServerPlayer player, String action) {
        deny(player, player.serverLevel(), player.blockPosition(), "deny-message", action);
    }
    public void deny(ServerPlayer player, ServerLevel level, BlockPos pos, String flag, String action) {
        long now = server.getTickCount();
        if (now - deniedMessages.getOrDefault(player.getUUID(), -100L) < 20) return;
        deniedMessages.put(player.getUUID(), now);
        Object value = value(level, pos, player, flag);
        String message = value == null ? "Здесь нельзя " + action + "." : value.toString().replace("%what%", action);
        player.displayClientMessage(Component.literal(message.replace('&', '§')).withStyle(ChatFormatting.RED), true);
        if(config.bool(null,"use-particle-effects",true)) level.sendParticles(player,net.minecraft.core.particles.ParticleTypes.SMOKE,false,
                pos.getX()+.5,pos.getY()+1d,pos.getZ()+.5,8,.15,.15,.15,0);
    }
    private static final class CandidateCache {
        final long revision;
        final Map<Long, List<Region>> chunks = new HashMap<>();
        CandidateCache(long revision) { this.revision = revision; }
    }
}
