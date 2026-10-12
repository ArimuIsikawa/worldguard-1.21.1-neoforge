package com.sk89q.worldguard.neoforge.rules;

import com.sk89q.worldguard.neoforge.ProtectionService;
import com.sk89q.worldguard.neoforge.WorldGuardConfig;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Legacy global/per-world rules that are independent of region flags. */
public final class WorldRulesEvents {
    private static ProtectionService service() { return WorldGuardMod.service(); }
    private static boolean enabled(ServerLevel level, String key) {
        return service() != null && service().config().bool(level.dimension().location().toString(), key, false);
    }

    @SubscribeEvent public void breakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player) || service() == null) return;
        if (!service().blacklist().checkBlock(level, event.getPos(), player, "on-break", event.getState())
                || !service().blacklist().check(level, event.getPos(), player, "on-destroy-with", player.getMainHandItem())) event.setCanceled(true);
    }
    @SubscribeEvent public void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player) || service() == null) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiple) {
            for (var block : multiple.getReplacedBlockSnapshots()) {
                if (!service().blacklist().checkBlock(level, block.getPos(), player, "on-place", block.getCurrentState())) {
                    event.setCanceled(true); return;
                }
            }
        } else if (!service().blacklist().checkBlock(level, event.getPos(), player, "on-place", event.getPlacedBlock())) event.setCanceled(true);
    }
    @SubscribeEvent public void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player) || service() == null) return;
        if (!service().blacklist().checkBlock(level, event.getPos(), player, "on-interact", level.getBlockState(event.getPos()))) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL);
        }
    }
    @SubscribeEvent public void acquire(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || service() == null) return;
        if (!service().blacklist().check(player.serverLevel(), event.getItemEntity().blockPosition(), player,
                "on-acquire", event.getItemEntity().getItem())) event.setCanPickup(TriState.FALSE);
    }

    @SubscribeEvent public void damage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || service() == null) return;
        DamageSource damage = event.getSource();
        if (event.getEntity() instanceof Wolf wolf && wolf.isTame() && !damage.is(DamageTypes.FELL_OUT_OF_WORLD)
                && enabled(level, "mobs.anti-wolf-dumbness") && damage.getEntity() == null) { event.setCanceled(true); return; }
        if (event.getEntity() instanceof ServerPlayer player) {
            if (damage.is(DamageTypes.DROWN) && service().waterBreathing(player)) { player.setAirSupply(player.getMaxAirSupply()); event.setCanceled(true); return; }
            var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            if (damage.is(DamageTypes.DROWN) && enabled(level, "default.pumpkin-scuba")
                    && (helmet.is(Items.CARVED_PUMPKIN) || helmet.is(Items.JACK_O_LANTERN))) {
                player.setAirSupply(player.getMaxAirSupply()); event.setCanceled(true); return;
            }
            if (damage.is(DamageTypes.FELL_OUT_OF_WORLD) && enabled(level, "player-damage.teleport-on-void-falling")) {
                if (rescue(player, level.getSharedSpawnPos()) && enabled(level, "player-damage.reset-fall-on-void-teleport")) player.resetFallDistance();
                event.setCanceled(true); return;
            }
            if (damage.is(DamageTypes.IN_WALL) && enabled(level, "player-damage.teleport-on-suffocation")) {
                if (rescue(player, player.blockPosition())) { event.setCanceled(true); return; }
            }
            boolean disabled = damage.is(DamageTypes.LAVA) && enabled(level, "player-damage.disable-lava-damage")
                    || damage.is(DamageTypeTags.IS_FIRE) && enabled(level, "player-damage.disable-fire-damage")
                    || damage.is(DamageTypes.LIGHTNING_BOLT) && enabled(level, "player-damage.disable-lightning-damage")
                    || damage.is(DamageTypes.DROWN) && enabled(level, "player-damage.disable-drowning-damage")
                    || damage.is(DamageTypes.IN_WALL) && enabled(level, "player-damage.disable-suffocation-damage")
                    || (damage.is(DamageTypes.CACTUS) || damage.is(DamageTypes.SWEET_BERRY_BUSH)) && enabled(level, "player-damage.disable-contact-damage")
                    || damage.is(DamageTypes.FELL_OUT_OF_WORLD) && enabled(level, "player-damage.disable-void-damage")
                    || damage.is(DamageTypeTags.IS_EXPLOSION) && enabled(level, "player-damage.disable-explosion-damage");
            if (disabled) { event.setCanceled(true); if (damage.is(DamageTypeTags.IS_FIRE)) player.clearFire(); }
        }
        Entity attacker = damage.getEntity();
        if (!(attacker instanceof ServerPlayer)) {
            if (event.getEntity() instanceof ArmorStand && enabled(level, "mobs.block-armor-stand-destroy")) event.setCanceled(true);
        }
    }

    private static boolean rescue(ServerPlayer player, BlockPos start) {
        ServerLevel level = player.serverLevel();
        int bottom = Math.max(level.getMinBuildHeight() + 1, start.getY()), top = Math.min(level.getMaxBuildHeight() - 2, bottom + 64);
        for (int y = bottom; y <= top; y++) for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                BlockPos target = new BlockPos(start.getX() + dx, y, start.getZ() + dz);
                if (!level.getBlockState(target.below()).isSolid() || !level.getBlockState(target).getCollisionShape(level, target).isEmpty()
                        || !level.getBlockState(target.above()).getCollisionShape(level, target.above()).isEmpty()
                        || !level.getFluidState(target).isEmpty() || !level.getFluidState(target.above()).isEmpty()) continue;
                if (!service().canEnter(player, level, target) || !service().canExit(player, level, target)) continue;
                player.teleportTo(level, target.getX() + .5, target.getY(), target.getZ() + .5, player.getYRot(), player.getXRot());
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent public void explosion(ExplosionEvent.Start event) {
        if (!(event.getLevel() instanceof ServerLevel level) || service() == null) return;
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (source instanceof WitherSkull && enabled(level, "mobs.block-wither-skull-explosions")
                || source instanceof AbstractWindCharge && enabled(level, "mobs.block-windcharge-explosions")) event.setCanceled(true);
        boolean other = !(source instanceof Creeper || source instanceof WitherSkull || source instanceof WitherBoss
                || source instanceof LargeFireball || source instanceof AbstractWindCharge || source instanceof PrimedTnt || source instanceof MinecartTNT || source instanceof EnderDragon);
        if (other && enabled(level, "mobs.block-other-explosions")) event.setCanceled(true);
    }
    @SubscribeEvent public void explosionBlocks(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || service() == null) return;
        Entity source = event.getExplosion().getDirectSourceEntity();
        String key = source instanceof Creeper ? "mobs.block-creeper-block-damage" : source instanceof WitherSkull ? "mobs.block-wither-skull-block-damage"
                : source instanceof WitherBoss ? "mobs.block-wither-block-damage" : source instanceof LargeFireball ? "mobs.block-fireball-block-damage"
                : source instanceof PrimedTnt || source instanceof MinecartTNT ? "ignition.block-tnt-block-damage" : null;
        if (key != null && enabled(level, key)) event.getAffectedBlocks().clear();
    }
    @SubscribeEvent public void grief(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || service() == null) return;
        String key = entity instanceof EnderMan ? "mobs.disable-enderman-griefing" : entity instanceof SnowGolem ? "mobs.disable-snowman-trails"
                : entity instanceof Zombie ? "mobs.block-zombie-door-destruction" : entity instanceof EnderDragon ? "mobs.block-enderdragon-block-damage" : null;
        if (key != null && enabled(level, key)) event.setCanGrief(false);
    }
    @SubscribeEvent public void spawn(MobSpawnEvent.SpawnPlacementCheck event) {
        ServerLevel level = event.getLevel().getLevel();
        if (event.getEntityType() == EntityType.SLIME && event.getSpawnType() == MobSpawnType.NATURAL && event.getPos().getY() >= 60
                && enabled(level, "mobs.block-above-ground-slimes")) event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }
    @SubscribeEvent public void falling(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || service() == null || event.loadedFromDisk()) return;
        if (event.getEntity() instanceof LightningBolt && !WorldRulesHooks.lightning(level, event.getEntity().blockPosition())) {
            event.setCanceled(true); return;
        }
        if (event.getEntity() instanceof FallingBlockEntity falling) {
            var state = falling.getBlockState();
            if (state.is(Blocks.GRAVEL) && enabled(level, "physics.no-physics-gravel")
                    || state.is(Blocks.SAND) && enabled(level, "physics.no-physics-sand")
                    || state.is(Blocks.RED_SAND) && enabled(level, "physics.no-physics-sand")) {
                event.setCanceled(true);
                BlockPos source = falling.getStartPos();
                if (level.getBlockState(source).isAir() || !level.getFluidState(source).isEmpty()) level.setBlock(source, state, 3);
            }
        }
    }
    @SubscribeEvent public void lightning(EntityStruckByLightningEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || service() == null) return;
        Entity entity = event.getEntity();
        if (entity instanceof Pig && enabled(level, "weather.disable-pig-zombification")
                || entity instanceof Villager && enabled(level, "weather.disable-villager-witchification")
                || entity instanceof Creeper && enabled(level, "weather.disable-powered-creepers")) event.setCanceled(true);
    }
    @SubscribeEvent public void mount(EntityMountEvent event) {
        if (event.isMounting() && !(event.getEntityMounting() instanceof ServerPlayer) && event.getLevel() instanceof ServerLevel level
                && enabled(level, "mobs.block-vehicle-entry")) event.setCanceled(true);
    }
    @SubscribeEvent public void trample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || service() == null) return;
        String key = event.getEntity() instanceof ServerPlayer ? "crops.disable-player-trampling" : "crops.disable-creature-trampling";
        if (enabled(level, key)) event.setCanceled(true);
    }

    @SubscribeEvent public void effect(MobEffectEvent.Applicable event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || service() == null || !(event.getEntity() instanceof ServerPlayer)) return;
        String id = BuiltInRegistries.MOB_EFFECT.getKey(event.getEffectInstance().getEffect().value()).toString();
        if (id.equals("minecraft:conduit_power") && enabled(level, "gameplay.disable-conduit-effects")) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY); return;
        }
        if(event.getEntity() instanceof ServerPlayer player && service().hasPermission(player,"worldguard.override.potions")) return;
        for (String effect : service().config().strings(level.dimension().location().toString(), "gameplay.block-potions")) {
            if (WorldRulesHooks.effectId(effect).equals(id)) { event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY); return; }
        }
    }

    @SubscribeEvent public void weather(ServerTickEvent.Pre event) {
        if (service() == null) return;
        service().processDeniedHoppers();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (enabled(level, "weather.disable-weather")) level.setWeatherParameters(6000, 6000, false, false);
            else if (level.isThundering() && enabled(level, "weather.disable-thunderstorm")) level.setWeatherParameters(0, 6000, level.isRaining(), false);
            else if (enabled(level, "weather.always-raining") || enabled(level, "weather.always-thundering")) {
                boolean thunder = enabled(level, "weather.always-thundering") && !enabled(level, "weather.disable-thunderstorm");
                level.setWeatherParameters(0, 6000, true, thunder);
            }
        }
    }
}
