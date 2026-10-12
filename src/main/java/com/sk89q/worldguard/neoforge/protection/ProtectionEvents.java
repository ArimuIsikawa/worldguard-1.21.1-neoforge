package com.sk89q.worldguard.neoforge.protection;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Container;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

import java.util.Collection;
import java.util.HashSet;

/** Region checks are made before NeoForge commits the corresponding operation. */
public final class ProtectionEvents {
    private final java.util.Map<Entity,net.minecraft.world.entity.MobSpawnType> spawnTypes=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static boolean active() { return WorldGuardMod.service() != null; }

    private static void deny(Entity actor, String action) {
        if (actor instanceof ServerPlayer player) WorldGuardMod.service().deny(player, action);
    }

    /** Damage sources retain projectile owners, and tame animals act on behalf of their owner. */
    public static Entity responsible(Entity entity) {
        for (int depth = 0; entity != null && depth < 8; depth++) {
            Entity next = entity instanceof Projectile projectile ? projectile.getOwner()
                    : entity instanceof net.minecraft.world.entity.OwnableEntity animal ? animal.getOwner() : null;
            if (next == null || next == entity) return entity;
            entity = next;
        }
        return entity;
    }

    public static boolean flag(ServerLevel level, BlockPos pos, Entity actor, String flag) {
        return !active() || WorldGuardMod.service().allows(level, pos, actor, flag);
    }

    public static boolean use(ServerLevel level, BlockPos pos, Entity actor, String flag) {
        return !active() || WorldGuardMod.service().canUse(level, pos, actor, flag);
    }

    public static boolean uses(ServerLevel level, BlockPos pos, Entity actor, String... flags) {
        return !active() || WorldGuardMod.service().canUse(level, pos, actor, flags);
    }

    public static boolean enabled(ServerLevel level, BlockPos pos, Entity actor, String flag) {
        if (!active()) return false;
        Object value = WorldGuardMod.service().value(level, pos, actor, flag);
        return Boolean.TRUE.equals(value) || "allow".equalsIgnoreCase(String.valueOf(value));
    }

    @SubscribeEvent
    public void breakBlock(BlockEvent.BreakEvent event) {
        if(event.getLevel() instanceof ServerLevel level && active() && !WorldGuardMod.service().buildPermission(level,event.getPlayer(),"block",BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).getPath(),"remove")) {
            event.setCanceled(true); return;
        }
        if (event.getLevel() instanceof ServerLevel level && !use(level, event.getPos(), event.getPlayer(), "block-break")) {
            event.setCanceled(true);
            deny(event.getPlayer(), "block-break");
        }
    }

    @SubscribeEvent
    public void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !active()) return;
        Entity actor = responsible(event.getEntity());
        // The goal injections evaluate the actual destination, before the Enderman clears its carried block.
        if (event.getEntity() instanceof EnderMan) return;
        if(!WorldGuardMod.service().buildPermission(level,actor,"block",BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).getPath(),"place")) { event.setCanceled(true); return; }
        boolean allowed = event instanceof BlockEvent.EntityMultiPlaceEvent multi
                ? multi.getReplacedBlockSnapshots().stream().allMatch(snapshot -> use(level, snapshot.getPos(), actor, "block-place"))
                : use(level, event.getPos(), actor, "block-place");
        if (!allowed) { event.setCanceled(true); deny(actor, "block-place"); }
    }

    @SubscribeEvent
    public void tool(BlockEvent.BlockToolModificationEvent event) {
        if(event.getLevel() instanceof ServerLevel level && active() && !WorldGuardMod.service().buildPermission(level,event.getPlayer(),"block",BuiltInRegistries.BLOCK.getKey(level.getBlockState(event.getPos()).getBlock()).getPath(),"interact")) { event.setCanceled(true); return; }
        if (event.getLevel() instanceof ServerLevel level && !use(level, event.getPos(), event.getPlayer(), "build")) {
            event.setCanceled(true);
            deny(event.getPlayer(), "build");
        }
    }

    @SubscribeEvent
    public void interactBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !active()) return;
        Player player = event.getEntity();
        if (player instanceof ServerPlayer operator && operator.hasPermissions(WorldGuardMod.service().config().operatorLevel())
                && com.sk89q.worldguard.neoforge.rules.BlacklistRules.normalizeId(WorldGuardMod.service().config().string(level.dimension().location().toString(),"regions.wand","minecraft:leather"))
                .equals(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString())) {
            var regions = WorldGuardMod.service().regions(level,event.getPos());
            operator.sendSystemMessage(net.minecraft.network.chat.Component.literal(regions.isEmpty() ? "No regions at " + event.getPos().toShortString()
                    : "Regions: " + regions.stream().map(r -> r.id() + " (priority " + r.priority() + ")").collect(java.util.stream.Collectors.joining(", "))));
            event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS); return;
        }
        if (!ProtectionHooks.blacklist(player, "on-use", event.getItemStack())) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); return;
        }
        BlockPos pos = event.getPos();
        if (!com.sk89q.worldguard.neoforge.rules.SignChestProtection.allows(level, pos, player instanceof ServerPlayer sp ? sp : null)) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); deny(player, "chest-access"); return;
        }
        BlockState state = level.getBlockState(pos);
        if(!WorldGuardMod.service().buildPermission(level,player,"block",BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(),"interact") || !itemPermission(level,player,event.getItemStack())) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); return;
        }
        boolean whitelisted = com.sk89q.worldguard.neoforge.rules.WorldRulesHooks.matches(level, "event-handling.interaction-whitelist", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
                || com.sk89q.worldguard.neoforge.rules.WorldRulesHooks.matches(level, "event-handling.interaction-whitelist", BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString());
        Block block = state.getBlock();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        String action = null;
        if (block instanceof SignBlock || block instanceof AbstractCauldronBlock || block instanceof SweetBerryBushBlock
                || block instanceof CaveVines || block instanceof PumpkinBlock || block instanceof FlowerPotBlock
                || block instanceof BrushableBlock) action = "build";
        else if (block instanceof AnvilBlock) action = "use-anvil";
        else if (block instanceof BedBlock) action = "sleep";
        else if (block instanceof RespawnAnchorBlock) action = "respawn-anchors";
        else if (blockEntity instanceof Container || blockEntity instanceof MenuProvider
                || state.getMenuProvider(level, pos) != null) action = "chest-access";
        else if (block instanceof TntBlock) action = "tnt";
        else if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof BellBlock
                || block instanceof NoteBlock || block instanceof JukeboxBlock || block instanceof RepeaterBlock
                || block instanceof ComparatorBlock || block instanceof CakeBlock || block instanceof AbstractCandleBlock
                || block instanceof ComposterBlock || block instanceof BeehiveBlock || block instanceof FlowerPotBlock
                || block instanceof CampfireBlock || block instanceof DecoratedPotBlock) action = "use";
        boolean doubleChestDenied = block instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE
                && !use(level, pos.relative(ChestBlock.getConnectedDirection(state)), player, "chest-access");
        boolean interactive = "use".equals(action) || "sleep".equals(action) || "respawn-anchors".equals(action) || "tnt".equals(action);
        if (!whitelisted && action != null && (!(interactive ? uses(level, pos, player, action, "interact") : use(level, pos, player, action)) || doubleChestDenied)) {
            event.setUseBlock(TriState.FALSE);
            // Stopping the full interaction prevents an item from mutating the denied container/door.
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            deny(player, action);
            return;
        }
        // Unknown mod blocks still receive the generic interaction restriction, while item placement
        // keeps its own block-place event and may be explicitly allowed independently.
        if (!whitelisted && action == null && !use(level, pos, player, "interact")) event.setUseBlock(TriState.FALSE);
        if (event.getItemStack().is(Items.FLINT_AND_STEEL) || event.getItemStack().is(Items.FIRE_CHARGE)) action = "lighter";
        else if (event.getItemStack().getItem() instanceof BoatItem || event.getItemStack().getItem() instanceof MinecartItem) action = "vehicle-place";
        else if (event.getItemStack().is(Items.TNT)) action = "tnt";
        else action = null;
        if (action != null) {
            BlockPos destination = event.getFace() == null ? pos : pos.relative(event.getFace());
            if (!use(level, pos, player, action) || !use(level, destination, player, action)) {
                event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); deny(player, action);
            }
        }
    }

    private static boolean interactEntity(Player player, Entity target) {
        if (!(target.level() instanceof ServerLevel level) || !active()) return true;
        if(!WorldGuardMod.service().buildPermission(level,player,"entity",BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath(),"interact")) return false;
        String action = target instanceof ArmorStand ? "build"
                : target instanceof ItemFrame frame ? frame.getItem().isEmpty() ? "build" : "item-frame-rotation"
                : target instanceof Container && player.isSecondaryUseActive() ? "chest-access"
                : target instanceof Boat || target instanceof AbstractMinecart || target instanceof net.minecraft.world.entity.animal.horse.AbstractHorse ? "ride" : "interact";
        if (action.equals("ride") ? uses(level, target.blockPosition(), player, "ride", "interact") : use(level, target.blockPosition(), player, action)) return true;
        deny(player, action);
        return false;
    }

    @SubscribeEvent
    public void interact(PlayerInteractEvent.EntityInteract event) {
        if (!interactEntity(event.getEntity(), event.getTarget())) { event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); }
    }

    @SubscribeEvent
    public void interactAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!interactEntity(event.getEntity(), event.getTarget())) { event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); }
    }

    @SubscribeEvent
    public void useItem(PlayerInteractEvent.RightClickItem event) {
        if(event.getLevel() instanceof ServerLevel level && !itemPermission(level,event.getEntity(),event.getItemStack())) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); return;
        }
        if (!ProtectionHooks.blacklist(event.getEntity(), "on-use", event.getItemStack())) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL);
        }
        if (event.getLevel() instanceof ServerLevel level && com.sk89q.worldguard.neoforge.rules.WorldRulesHooks.matches(level,
                "event-handling.emit-block-use-at-feet", BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString())
                && !use(level, event.getEntity().blockPosition(), event.getEntity(), "interact")) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void attack(AttackEntityEvent event) {
        if (!(event.getTarget().level() instanceof ServerLevel level) || !active()) return;
        Entity target = event.getTarget();
        String permissionAction=target instanceof net.minecraft.world.entity.LivingEntity && !(target instanceof ArmorStand) ? "damage" : "remove";
        if(!WorldGuardMod.service().buildPermission(level,event.getEntity(),"entity",BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath(),permissionAction)) { event.setCanceled(true); return; }
        String action = target instanceof Boat || target instanceof AbstractMinecart ? "vehicle-destroy"
                : target instanceof Painting ? "entity-painting-destroy"
                : target instanceof ItemFrame ? "entity-item-frame-destroy"
                : target instanceof ArmorStand || target instanceof HangingEntity ? "build"
                : target instanceof Animal ? "damage-animals" : null;
        if (action != null && !use(level, target.blockPosition(), event.getEntity(), action)) {
            event.setCanceled(true); deny(event.getEntity(), action);
        }
    }

    @SubscribeEvent
    public void damage(LivingIncomingDamageEvent event) {
        Entity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || !active()) return;
        DamageSource source = event.getSource();
        Entity direct = source.getDirectEntity();
        Entity actor = responsible(source.getEntity() != null ? source.getEntity() : direct);
        BlockPos pos = target.blockPosition();
        if(actor instanceof ServerPlayer && !WorldGuardMod.service().buildPermission(level,actor,"entity",BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath(),"damage")) { event.setCanceled(true); return; }
        boolean invincible = target instanceof Player && enabled(level, pos, target, "invincible");
        if (invincible) {
            target.clearFire();
            if (actor instanceof Mob mob && !(mob instanceof TamableAnimal tame && tame.isTame())
                    && !(mob instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse && horse.isTamed())
                    && WorldGuardMod.service().config().bool(level.dimension().location().toString(), "regions.invincibility-removes-mobs", false)) mob.discard();
        }
        boolean denied = target instanceof Player && (invincible
                || (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)) && !WorldGuardMod.service().testState(level, pos, target, "fall-damage"));
        if (target instanceof Player) {
            if (actor instanceof Player && actor != target) {
                denied |= !flag(level, pos, actor, "pvp") || !flag(level, actor.blockPosition(), actor, "pvp");
            } else if (actor instanceof Mob) denied |= !WorldGuardMod.service().testState(level, pos, target, "mob-damage");
        }
        if (target instanceof Animal && actor instanceof Player) denied |= !use(level, pos, actor, "damage-animals");
        if (direct instanceof FireworkRocketEntity) denied |= !flag(level, pos, actor, "firework-damage");
        if (direct instanceof ThrownPotion) denied |= !flag(level, pos, actor, "potion-splash");
        if (source.is(DamageTypeTags.IS_EXPLOSION) && explosionEntityCheck(level,explosionFlag(direct,actor))) denied |= !flag(level, pos, actor, explosionFlag(direct, actor));
        if (source.is(DamageTypes.LIGHTNING_BOLT)) denied |= !flag(level, pos, target, "lightning");
        if (denied) { event.setCanceled(true); deny(actor, "damage"); }
    }

    public static String explosionFlag(Entity direct, Entity owner) {
        if (direct instanceof net.minecraft.world.entity.item.PrimedTnt || direct instanceof net.minecraft.world.entity.vehicle.MinecartTNT) return "tnt";
        if (direct instanceof Creeper || owner instanceof Creeper) return "creeper-explosion";
        if (direct instanceof LargeFireball || owner instanceof Ghast) return "ghast-fireball";
        if (direct instanceof WitherSkull || direct instanceof WitherBoss || owner instanceof WitherBoss) return "wither-damage";
        if (direct instanceof BreezeWindCharge) return "breeze-charge-explosion";
        if (direct instanceof WindCharge) return "wind-charge-burst";
        return "other-explosion";
    }

    @SubscribeEvent
    public void explosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !active()) return;
        Explosion explosion = event.getExplosion();
        Entity direct = explosion.getDirectSourceEntity();
        Entity actor = responsible(explosion.getIndirectSourceEntity());
        String flag = explosionFlag(direct, actor);
        BlockPos origin = BlockPos.containing(explosion.center());
        event.getAffectedBlocks().removeIf(pos -> !flag(level, pos, actor, flag)
                || !com.sk89q.worldguard.neoforge.rules.SignChestProtection.allows(level, pos, actor instanceof ServerPlayer player ? player : null)
                || level.getBlockState(pos).getBlock() instanceof TntBlock && !flag(level, pos, actor, "tnt")
                || !explosionBuild(level, origin, pos, actor, flag));
        event.getAffectedEntities().removeIf(target -> explosionEntityCheck(level,flag) && !flag(level, target.blockPosition(), actor, flag)
                || target instanceof Player && enabled(level, target.blockPosition(), target, "invincible")
                || target instanceof Player && actor instanceof Player && actor != target
                && (!flag(level, target.blockPosition(), actor, "pvp") || !flag(level, actor.blockPosition(), actor, "pvp"))
                || target instanceof Animal && actor instanceof Player && !use(level, target.blockPosition(), actor, "damage-animals"));
    }

    private static boolean explosionBuild(ServerLevel level, BlockPos origin, BlockPos target, Entity actor, String explosionFlag) {
        String[] flags = explosionFlag.equals("tnt") ? new String[]{"block-break", "tnt"} : new String[]{"block-break"};
        return actor instanceof Player ? uses(level, target, actor, flags)
                : WorldGuardMod.service().canAutomateUses(level, actor==null ? origin : WorldGuardMod.service().origin(actor), target, false, flags);
    }

    private static boolean itemPermission(ServerLevel level, Entity actor, net.minecraft.world.item.ItemStack item) {
        if(!active() || item.isEmpty()) return true;
        if(!(item.getItem() instanceof net.minecraft.world.item.BlockItem) && !WorldGuardMod.service().buildPermission(level,actor,"item",BuiltInRegistries.ITEM.getKey(item.getItem()).getPath(),"use")) return false;
        return com.sk89q.worldguard.neoforge.rules.WorldRulesHooks.potionUse(level,actor,item);
    }
    private static boolean explosionEntityCheck(ServerLevel level, String flag) {
        return flag.equals("tnt") || WorldGuardMod.service().config().bool(level.dimension().location().toString(),"regions.explosion-flags-block-entity-damage",true);
    }

    public static boolean spawn(ServerLevel level, BlockPos pos, net.minecraft.world.entity.EntityType<?> type, Entity entity) {
        if(!active()) return true;
        String dimension = level.dimension().location().toString();
        boolean tame = entity instanceof TamableAnimal animal && animal.isTame()
                || entity instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse && horse.isTamed();
        if (tame && WorldGuardMod.service().config().bool(dimension, "mobs.allow-tamed-spawns", true)) return true;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        if (WorldGuardMod.service().config().strings(dimension, "mobs.block-creature-spawn").stream()
                .anyMatch(value -> com.sk89q.worldguard.neoforge.rules.BlacklistRules.normalizeId(value).equals(id))) return false;
        if(!WorldGuardMod.service().config().bool(null,"regions.use-creature-spawn-event",true)) return true;
        if (!flag(level, pos, null, "mob-spawning")) return false;
        Collection<?> denied = WorldGuardMod.service().setValues(level, pos, null, "deny-spawn");
        return denied.stream().noneMatch(value -> id.equals(String.valueOf(value))
                || id.substring(id.indexOf(':') + 1).equals(String.valueOf(value)));
    }

    @SubscribeEvent
    public void spawnPosition(MobSpawnEvent.PositionCheck event) {
        spawnTypes.put(event.getEntity(),event.getSpawnType());
        if(active() && !WorldGuardMod.service().config().bool(null,"regions.use-pre-creature-spawn-event",true)) return;
        if (active() && !spawn(event.getLevel().getLevel(), event.getEntity().blockPosition(), event.getEntity().getType(), event.getEntity())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public void finalizeSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
        spawnTypes.put(event.getEntity(),event.getSpawnType());
        if(!active()) return;
        ServerLevel level=event.getLevel().getLevel();
        boolean egg=event.getSpawnType()==net.minecraft.world.entity.MobSpawnType.SPAWN_EGG || event.getSpawnType()==net.minecraft.world.entity.MobSpawnType.DISPENSER;
        if(egg && ProtectionHooks.itemActor()==null && WorldGuardMod.service().config().bool(level.dimension().location().toString(),"event-handling.block-entity-spawns-with-untraceable-cause",false)
                && !use(level,event.getEntity().blockPosition(),null,"block-place")) event.setSpawnCancelled(true);
    }

    @SubscribeEvent
    public void join(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !active()) return;
        WorldGuardMod.service().rememberOrigin(event.getEntity());
        if(event.loadedFromDisk()) return;
        Entity entity = event.getEntity();
        Entity actor=ProtectionHooks.itemActor();
        boolean constructed=entity instanceof Mob || entity instanceof ArmorStand || entity instanceof HangingEntity || entity instanceof net.minecraft.world.entity.vehicle.VehicleEntity;
        if(actor instanceof ServerPlayer && constructed) {
            if(!WorldGuardMod.service().buildPermission(level,actor,"entity",BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath(),"place")
                    || !use(level,entity.blockPosition(),actor,entity instanceof net.minecraft.world.entity.vehicle.VehicleEntity ? "vehicle-place" : "block-place")) {
                event.setCanceled(true); return;
            }
        }
        var spawnType=spawnTypes.remove(entity);
        boolean custom=spawnType==null && actor==null;
        if (entity instanceof Mob && (!custom || WorldGuardMod.service().config().bool(level.dimension().location().toString(),"mobs.block-plugin-spawning",true))
                && !spawn(level, entity.blockPosition(), entity.getType(), entity)) event.setCanceled(true);
        else if (entity instanceof LightningBolt && !flag(level, entity.blockPosition(), null, "lightning")) event.setCanceled(true);
    }

    @SubscribeEvent
    public void grief(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !active()) return;
        String flag = entity instanceof EnderMan ? "enderman-grief" : entity instanceof SnowGolem ? "snowman-trails"
                : entity instanceof Ravager ? "ravager-grief" : entity instanceof EnderDragon ? "enderdragon-block-damage"
                : entity instanceof WitherBoss || entity instanceof WitherSkull ? "wither-damage"
                : entity instanceof LargeFireball || entity instanceof Ghast ? "ghast-fireball"
                : entity instanceof Creeper ? "creeper-explosion" : null;
        if (flag != null && !flag(level, entity.blockPosition(), null, flag)) event.setCanGrief(false);
    }

    @SubscribeEvent
    public void piston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !active()) return;
        BlockPos piston = event.getPos();
        if (!WorldGuardMod.service().canAutomate(level, piston, piston, "pistons", false)) { event.setCanceled(true); return; }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) return;
        Direction movement = event.getPistonMoveType().isExtend ? event.getDirection() : event.getDirection().getOpposite();
        for (BlockPos source : resolver.getToPush()) {
            BlockPos destination = source.relative(movement);
            if (!WorldGuardMod.service().canAutomate(level, piston, source, "pistons", false)
                    || !WorldGuardMod.service().canAutomate(level, piston, destination, "pistons", false)
                    || !ProtectionHooks.canCrossBoundary(level, piston, source)
                    || !ProtectionHooks.canCrossBoundary(level, piston, destination)) { event.setCanceled(true); return; }
        }
        for (BlockPos broken : resolver.getToDestroy()) {
            if (!WorldGuardMod.service().canAutomate(level, piston, broken, "pistons", false) || !ProtectionHooks.canCrossBoundary(level, piston, broken)) {
                event.setCanceled(true); return;
            }
        }
        BlockPos head = piston.relative(event.getDirection());
        if (!WorldGuardMod.service().canAutomate(level, piston, head, "pistons", false) || !ProtectionHooks.canCrossBoundary(level, piston, head)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void trample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof ServerLevel level && !use(level, event.getPos(), responsible(event.getEntity()), "block-trampling")) event.setCanceled(true);
    }

    @SubscribeEvent
    public void liquidChange(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !active()) return;
        String flag = event.getNewState().getBlock() instanceof BaseFireBlock ? "lava-fire" : "lava-harden";
        if (!flag(level, event.getPos(), null, flag)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void pickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer().level() instanceof ServerLevel level && !flag(level, event.getItemEntity().blockPosition(), event.getPlayer(), "item-pickup")) event.setCanPickup(TriState.FALSE);
    }

    @SubscribeEvent
    public void experience(LivingExperienceDropEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level && !flag(level, event.getEntity().blockPosition(), event.getAttackingPlayer(), "exp-drops")) event.setCanceled(true);
    }

    @SubscribeEvent
    public void mount(EntityMountEvent event) {
        if (event.isMounting() && event.getLevel() instanceof ServerLevel level
                && !uses(level, event.getEntityBeingMounted().blockPosition(), event.getEntityMounting(), "ride", "interact")) {
            event.setCanceled(true); deny(event.getEntityMounting(), "ride");
        }
    }

    @SubscribeEvent
    public void sleep(CanPlayerSleepEvent event) {
        if (event.getLevel() instanceof ServerLevel level && !uses(level, event.getPos(), event.getEntity(), "sleep", "interact")) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM); deny(event.getEntity(), "sleep");
        }
    }

    @SubscribeEvent
    public void teleport(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !active()) return;
        ServerLevel level = player.serverLevel();
        BlockPos destination = BlockPos.containing(event.getTarget());
        String action = event instanceof EntityTeleportEvent.EnderPearl ? "enderpearl"
                : event instanceof EntityTeleportEvent.ChorusFruit ? "chorus-fruit-teleport" : null;
        boolean allowed = action == null || flag(level, player.blockPosition(), player, action) && flag(level, destination, player, action);
        allowed &= WorldGuardMod.service().sessions().canTravel(player, level, destination, true);
        if (!allowed) { event.setCanceled(true); deny(player, action == null ? "teleport" : action); }
    }

    @SubscribeEvent
    public void projectile(ProjectileImpactEvent event) {
        if (!(event.getProjectile().level() instanceof ServerLevel level) || !active()) return;
        Projectile projectile = event.getProjectile();
        Entity actor = responsible(projectile);
        BlockPos pos = BlockPos.containing(event.getRayTraceResult().getLocation());
        if (projectile instanceof net.minecraft.world.entity.projectile.SmallFireball && event.getRayTraceResult() instanceof BlockHitResult hit) {
            BlockPos fire = hit.getBlockPos().relative(hit.getDirection());
            boolean allowed = actor instanceof Player ? uses(level, fire, actor, "lighter", "block-place")
                    : WorldGuardMod.service().canAutomateUses(level, projectile.blockPosition(), fire, false, "lighter", "block-place");
            if (!allowed) { event.setCanceled(true); projectile.discard(); return; }
        }
        if (projectile instanceof ThrownPotion && !flag(level, pos, actor, "potion-splash")) {
            event.setCanceled(true); projectile.discard(); return;
        }
        if (projectile instanceof WindCharge && !flag(level, pos, actor, "wind-charge-burst")
                || projectile instanceof BreezeWindCharge && !flag(level, pos, null, "breeze-charge-explosion")) {
            event.setCanceled(true); projectile.discard(); return;
        }
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof HangingEntity
                && !use(level, hit.getEntity().blockPosition(), actor, hit.getEntity() instanceof Painting ? "entity-painting-destroy" : "entity-item-frame-destroy")) {
            event.setCanceled(true); projectile.discard();
        }
    }
}
