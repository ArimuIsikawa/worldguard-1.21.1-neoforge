package com.sk89q.worldguard.neoforge.protection;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;

/** Small, server-only entry points shared by the vanilla injection sites. */
public final class ProtectionHooks {
    private ProtectionHooks() {}

    private record Cause(String flag, Entity actor, BlockPos origin, Cause previous) {}
    private static final ThreadLocal<Cause> CAUSE = new ThreadLocal<>();
    private static final ThreadLocal<Entity> ITEM_ACTOR = new ThreadLocal<>();
    public static Entity itemActor() { return ITEM_ACTOR.get(); }
    public static final class ItemScope implements AutoCloseable {
        private final Entity previous=ITEM_ACTOR.get();
        private ItemScope(Entity actor) { if(actor==null) ITEM_ACTOR.remove(); else ITEM_ACTOR.set(actor); }
        @Override public void close() { if(previous==null) ITEM_ACTOR.remove(); else ITEM_ACTOR.set(previous); }
    }
    public static ItemScope itemScope(Entity actor) { return new ItemScope(actor); }

    public static final class Scope implements AutoCloseable {
        private final Cause previous;
        private Scope(String flag, Entity actor, BlockPos origin) {
            previous = CAUSE.get();
            CAUSE.set(new Cause(flag, actor,origin==null ? null : origin.immutable(), previous));
        }
        @Override public void close() {
            if (previous == null) CAUSE.remove(); else CAUSE.set(previous);
        }
    }

    public static Scope scope(String flag, Entity actor) { return new Scope(flag, actor,null); }
    public static Scope scope(String flag, Entity actor,BlockPos origin) { return new Scope(flag,actor,origin); }

    public static String naturalFlag(BlockState state, boolean random) {
        Block block = state.getBlock();
        if (block instanceof FireBlock) return "fire-spread";
        if (block instanceof FrostedIceBlock) return "frosted-ice-melt";
        if (block instanceof IceBlock) return "ice-melt";
        if (block instanceof SnowLayerBlock) return "snow-melt";
        if (block instanceof FarmBlock) return "soil";
        if (block instanceof BaseCoralPlantTypeBlock || block instanceof CoralBlock) return "coral-fade";
        if (!random) return null;
        if (block instanceof LeavesBlock) return "leaf-decay";
        if (block instanceof MyceliumBlock) return "mycelium-spread";
        if (block instanceof SpreadingSnowyDirtBlock || block instanceof NyliumBlock) return "grass-growth";
        if (block instanceof MushroomBlock) return "mushroom-growth";
        if (block instanceof VineBlock || block instanceof GrowingPlantHeadBlock) return "vine-growth";
        if (block instanceof BuddingAmethystBlock || block instanceof PointedDripstoneBlock) return "rock-growth";
        if (block instanceof WeatheringCopper) return "copper-fade";
        if (block instanceof CropBlock || block instanceof PitcherCropBlock || block instanceof StemBlock || block instanceof SugarCaneBlock
                || block instanceof CactusBlock || block instanceof CocoaBlock || block instanceof NetherWartBlock
                || block instanceof SweetBerryBushBlock || block instanceof BambooStalkBlock
                || block instanceof BambooSaplingBlock || block instanceof SaplingBlock
                || block instanceof ChorusFlowerBlock || block instanceof MangrovePropaguleBlock) return "crop-growth";
        return null;
    }

    public static boolean allowChange(Level level, BlockPos pos, BlockState replacement) {
        if (!(level instanceof ServerLevel server) || WorldGuardMod.service() == null) return true;
        Cause cause = CAUSE.get();
        if (cause == null || cause.flag == null) return true;
        String flag = cause.flag;
        BlockState old = level.getBlockState(pos);
        if (!old.equals(replacement) && !com.sk89q.worldguard.neoforge.rules.SignChestProtection.allows(server, pos,
                cause.actor instanceof ServerPlayer player ? player : null)) return false;
        if (!com.sk89q.worldguard.neoforge.rules.WorldRulesHooks.naturalChange(server, pos, old, replacement, flag)) return false;
        if(flag.equals("tree-growth")) {
            if(!WorldGuardMod.service().allows(server,pos,cause.actor,"crop-growth")) return false;
            return cause.actor instanceof ServerPlayer ? WorldGuardMod.service().canUse(server,pos,cause.actor,"block-place")
                    : WorldGuardMod.service().canAutomate(server,cause.origin,pos,"block-place",false);
        }
        if((flag.equals("fire-spread") || flag.equals("lava-fire")) && cause.origin!=null && replacement.getBlock() instanceof BaseFireBlock && !(old.getBlock() instanceof BaseFireBlock)
                && !WorldGuardMod.service().canAutomate(server,cause.origin,pos,"block-place",false)) return false;
        if (flag.equals("weather")) {
            if (replacement.is(Blocks.SNOW)) flag = "snow-fall";
            else if (replacement.is(Blocks.ICE)) flag = "ice-form";
            else return true;
        } else if (flag.equals("soil")) {
            flag = replacement.getBlock() instanceof FarmBlock ? "moisture-change" : "soil-dry";
        } else if (flag.equals("enchantment")) {
            flag = replacement.is(Blocks.FROSTED_ICE) ? "frosted-ice-form" : "build";
            return WorldGuardMod.service().canUse(server,pos,cause.actor,flag);
        } else if (flag.equals("dispenser")) {
            return WorldGuardMod.service().canBuild(server, pos, null);
        } else if (flag.equals("mob")) {
            return mobChange(server, pos, cause.actor, replacement.isAir() ? "block-break" : "block-place");
        }
        if (flag.equals("build")) return WorldGuardMod.service().canBuild(server, pos, cause.actor);
        return WorldGuardMod.service().allows(server, pos, cause.actor, flag);
    }

    public static boolean allowDestroy(Level level, BlockPos pos, Entity actor) {
        if (!(level instanceof ServerLevel server) || WorldGuardMod.service() == null) return true;
        Entity owner = ProtectionEvents.responsible(actor);
        if (!com.sk89q.worldguard.neoforge.rules.SignChestProtection.allows(server, pos,
                owner instanceof ServerPlayer player ? player : null)) return false;
        Cause cause = CAUSE.get();
        if (cause != null) return allowChange(level, pos, Blocks.AIR.defaultBlockState());
        if (actor instanceof net.minecraft.world.entity.Mob) return mobChange(server, pos, actor);
        return true;
    }

    public static boolean mobChange(ServerLevel level, BlockPos pos, Entity mob) {
        return mobChange(level, pos, mob, "block-break");
    }

    public static boolean mobChange(ServerLevel level, BlockPos pos, Entity mob, String action) {
        if (WorldGuardMod.service() == null || mob == null) return true;
        String flag = mob instanceof net.minecraft.world.entity.monster.EnderMan ? "enderman-grief"
                : mob instanceof net.minecraft.world.entity.animal.SnowGolem ? "snowman-trails"
                : mob instanceof net.minecraft.world.entity.monster.Ravager ? "ravager-grief"
                : mob instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon ? "enderdragon-block-damage"
                : mob instanceof net.minecraft.world.entity.boss.wither.WitherBoss ? "wither-damage" : null;
        if (flag != null && (WorldGuardMod.service().config().denied(level.dimension().location().toString(), flag)
                || !WorldGuardMod.service().canAutomate(level, WorldGuardMod.service().origin(mob), pos, flag, false))) return false;
        Entity owner = ProtectionEvents.responsible(mob);
        return owner instanceof Player ? ProtectionEvents.use(level, pos, owner, action)
                : WorldGuardMod.service().canAutomate(level, WorldGuardMod.service().origin(mob), pos, action, false);
    }

    /** Automation can operate inside a claim; movement across a protected boundary is rejected. */
    public static boolean canCrossBoundary(ServerLevel level, BlockPos source, BlockPos destination) {
        if (WorldGuardMod.service() == null) return true;
        if (!com.sk89q.worldguard.neoforge.rules.SignChestProtection.allows(level, source, null)
                || !com.sk89q.worldguard.neoforge.rules.SignChestProtection.allows(level, destination, null)) return false;
        return WorldGuardMod.service().canAutomate(level, source, destination, "build", false);
    }

    public static boolean inventory(ServerLevel level, BlockPos initiator, BlockPos inventory) {
        var service = WorldGuardMod.service();
        if (service == null) return true;
        if (!com.sk89q.worldguard.neoforge.rules.SignChestProtection.allows(level, inventory, null)) return false;
        if (service.config().bool(level.dimension().location().toString(),"event-handling.ignore-hopper-item-move-events",false)) return true;
        return service.canAutomate(level, initiator, inventory, "chest-access", false);
    }

    public static boolean allowDrop(Player player) {
        if (!(player.level() instanceof ServerLevel level) || WorldGuardMod.service() == null) return true;
        if (ProtectionEvents.flag(level, player.blockPosition(), player, "item-drop")) return true;
        if (player instanceof ServerPlayer serverPlayer) WorldGuardMod.service().deny(serverPlayer, "item-drop");
        return false;
    }

    public static boolean blacklist(Player player, String event, net.minecraft.world.item.ItemStack stack) {
        return !(player instanceof ServerPlayer serverPlayer) || WorldGuardMod.service() == null
                || WorldGuardMod.service().blacklist().check(serverPlayer.serverLevel(), player.blockPosition(), serverPlayer, event, stack);
    }

    public static boolean allowNonlivingDamage(Entity target, DamageSource source) {
        if (!(target.level() instanceof ServerLevel level) || WorldGuardMod.service() == null) return true;
        Entity actor = ProtectionEvents.responsible(source.getEntity() != null ? source.getEntity() : source.getDirectEntity());
        if(!WorldGuardMod.service().buildPermission(level,actor,"entity",net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath(),"remove")) return false;
        String flag = target instanceof VehicleEntity ? "vehicle-destroy" : target instanceof Painting ? "entity-painting-destroy"
                : target instanceof ItemFrame ? "entity-item-frame-destroy" : target instanceof ArmorStand ? "build" : null;
        if (flag == null) return true;
        String configKey = target instanceof Painting ? "mobs.block-painting-destroy" : target instanceof ItemFrame ? "mobs.block-item-frame-destroy"
                : target instanceof ArmorStand ? "mobs.block-armor-stand-destroy" : null;
        if (!(actor instanceof Player) && configKey != null
                && WorldGuardMod.service().config().bool(level.dimension().location().toString(), configKey, false)) return false;
        boolean allowed = actor instanceof Player ? ProtectionEvents.use(level, target.blockPosition(), actor, flag)
                : ProtectionEvents.flag(level, target.blockPosition(), actor, flag);
        if (!allowed && actor instanceof ServerPlayer player) WorldGuardMod.service().deny(player, flag);
        return allowed;
    }

    public static boolean potion(Entity target, Entity owner, MobEffect effect) {
        if (!(target.level() instanceof ServerLevel level) || WorldGuardMod.service() == null) return true;
        Entity actor = ProtectionEvents.responsible(owner);
        BlockPos pos = target.blockPosition();
        if (!ProtectionEvents.flag(level, pos, actor, "potion-splash")) return false;
        if (effect != null && effect.getCategory() == MobEffectCategory.HARMFUL) {
            if (target instanceof Player && ProtectionEvents.enabled(level, pos, target, "invincible")) return false;
            if (actor instanceof Player && target instanceof Player && actor != target
                    && (!ProtectionEvents.flag(level, pos, actor, "pvp") || !ProtectionEvents.flag(level, actor.blockPosition(), actor, "pvp"))) return false;
            if (actor instanceof Player && target instanceof Animal && !ProtectionEvents.use(level, pos, actor, "damage-animals")) return false;
        }
        return true;
    }
}
