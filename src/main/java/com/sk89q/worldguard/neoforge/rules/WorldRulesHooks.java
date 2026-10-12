package com.sk89q.worldguard.neoforge.rules;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Locale;

/** Global legacy rules used by vanilla block/fluid injection sites. */
public final class WorldRulesHooks {
    public static boolean potionUse(ServerLevel level, net.minecraft.world.entity.Entity actor, net.minecraft.world.item.ItemStack stack) {
        var service=WorldGuardMod.service();
        if(service==null || !(stack.is(net.minecraft.world.item.Items.POTION) || stack.is(net.minecraft.world.item.Items.SPLASH_POTION) || stack.is(net.minecraft.world.item.Items.LINGERING_POTION))) return true;
        var contents=stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if(contents==null) return true;
        var blocked=service.config().strings(level.dimension().location().toString(),"gameplay.block-potions").stream().map(WorldRulesHooks::effectId).collect(java.util.stream.Collectors.toSet());
        for(var effect : contents.getAllEffects()) if(blocked.contains(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString())) {
            boolean throwable=stack.is(net.minecraft.world.item.Items.SPLASH_POTION) || stack.is(net.minecraft.world.item.Items.LINGERING_POTION);
            return actor instanceof net.minecraft.server.level.ServerPlayer player && service.hasPermission(player,"worldguard.override.potions")
                    && !(throwable && service.config().bool(level.dimension().location().toString(),"gameplay.block-potions-overly-reliably",false));
        }
        return true;
    }
    private WorldRulesHooks() { }
    public static String effectId(String legacyName) {
        String name = legacyName.toLowerCase(Locale.ROOT).trim();
        name = switch (name) {
            case "slow" -> "slowness";
            case "fast_digging" -> "haste";
            case "slow_digging" -> "mining_fatigue";
            case "increase_damage" -> "strength";
            case "heal" -> "instant_health";
            case "harm" -> "instant_damage";
            case "jump" -> "jump_boost";
            case "confusion" -> "nausea";
            case "damage_resistance" -> "resistance";
            default -> name;
        };
        return name.contains(":") ? name : "minecraft:" + name;
    }
    private static boolean configured(ServerLevel level, String key) {
        return WorldGuardMod.service() != null && WorldGuardMod.service().config().bool(level.dimension().location().toString(), key, false);
    }
    public static boolean bool(ServerLevel level, String key) { return configured(level, key); }
    public static boolean matches(ServerLevel level, String key, String id) {
        if (WorldGuardMod.service() == null) return false;
        for (String value : values(level, key)) {
            String candidate = value.trim().toLowerCase(Locale.ROOT);
            if (!candidate.contains(":")) candidate = "minecraft:" + candidate;
            if (candidate.equals(id)) return true;
        }
        return false;
    }
    public static boolean lightning(ServerLevel level, BlockPos position) {
        if (WorldGuardMod.service() == null) return true;
        BlockState target = level.getBlockState(position);
        if (target.isAir()) target = level.getBlockState(position.below());
        return !contains(values(level, "weather.prevent-lightning-strike-blocks"), target);
    }
    private static List<String> values(ServerLevel level, String key) {
        return WorldGuardMod.service().config().strings(level.dimension().location().toString(), key);
    }
    private static boolean contains(List<String> values, BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        for (String value : values) if (BlacklistRules.normalizeId(value).equals(id)) return true;
        return false;
    }

    public static boolean tick(ServerLevel level, BlockPos pos, BlockState state) {
        if (WorldGuardMod.service() == null) return true;
        return !(state.is(Blocks.GRAVEL) && configured(level, "physics.no-physics-gravel")
                || (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) && configured(level, "physics.no-physics-sand"));
    }

    public static boolean flow(ServerLevel level, BlockPos source, BlockPos destination, BlockState target, Fluid incoming) {
        if (WorldGuardMod.service() == null) return true;
        if (incoming.is(FluidTags.WATER)) {
            if (contains(values(level, "physics.disable-water-damage-blocks"), target)) return false;
            if (configured(level, "simulation.sponge.enable")) {
                int radius = Math.max(0, WorldGuardMod.service().config().integer(level.dimension().location().toString(), "simulation.sponge.radius", 3) - 1);
                boolean redstone = configured(level, "simulation.sponge.redstone");
                for (BlockPos candidate : BlockPos.betweenClosed(destination.offset(-radius, -radius, -radius), destination.offset(radius, radius, radius))) {
                    if (level.getBlockState(candidate).is(Blocks.SPONGE) && (!redstone || !level.hasNeighborSignal(candidate))) return false;
                }
            }
        }
        if (incoming.is(FluidTags.LAVA)) {
            List<String> allowed = values(level, "fire.lava-spread-blocks");
            if (!allowed.isEmpty() && !contains(allowed, level.getBlockState(destination.below()))) return false;
        }
        return true;
    }

    public static boolean naturalChange(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState, String cause) {
        if (WorldGuardMod.service() == null || cause == null) return true;
        if (cause.equals("weather") && newState.is(Blocks.SNOW)) {
            List<String> allowed = values(level, "dynamics.snow-fall-blocks");
            if (!allowed.isEmpty() && !contains(allowed, level.getBlockState(pos.below()))) return false;
        }
        if (cause.equals("lightning") && newState.getBlock() instanceof BaseFireBlock && configured(level, "weather.disable-lightning-strike-fire")) return false;
        if (cause.equals("fire-spread") && newState.getBlock() instanceof BaseFireBlock) {
            List<String> denied = values(level, "fire.disable-fire-spread-blocks");
            if (contains(denied, level.getBlockState(pos.below()))) return false;
            for (Direction direction : Direction.Plane.HORIZONTAL) if (contains(denied, level.getBlockState(pos.relative(direction)))) return false;
        }
        return true;
    }
}
