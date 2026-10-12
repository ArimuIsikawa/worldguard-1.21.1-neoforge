package com.sk89q.worldguard.neoforge.rules;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Optional original [Lock] protection, including modern signs and double chest halves. */
public final class SignChestProtection {
    private static boolean enabled(ServerLevel level) {
        return WorldGuardMod.service() != null && WorldGuardMod.service().config()
                .bool(level.dimension().location().toString(), "chest-protection.enable", false);
    }
    private static boolean bypass(@Nullable ServerPlayer player) {
        return player != null && WorldGuardMod.service() != null && WorldGuardMod.service().bypass(player);
    }

    public static boolean allows(ServerLevel level, BlockPos position, @Nullable ServerPlayer player) {
        if (!enabled(level) || bypass(player)) return true;
        if (!WorldGuardMod.service().healthy()) return false;
        for (BlockPos sign : signsFor(level, position)) {
            if (!(level.getBlockEntity(sign) instanceof SignBlockEntity entity)) continue;
            if (!ownerAllows(lines(entity.getFrontText()), player == null ? null : player.getUUID(), player == null ? null : player.getGameProfile().getName())
                    || !ownerAllows(lines(entity.getBackText()), player == null ? null : player.getUUID(), player == null ? null : player.getGameProfile().getName())) return false;
        }
        return true;
    }

    /** True for ordinary signs, or when the actor appears on any owner line of a lock sign. */
    public static boolean ownerAllows(String[] lines, @Nullable UUID uuid, @Nullable String name) {
        return SignOwners.allows(lines, uuid, name);
    }
    public static boolean isLock(String[] lines) { return SignOwners.isLock(lines); }
    private static boolean matchesOwner(String owner, @Nullable UUID uuid, @Nullable String name) {
        return SignOwners.matchesOwner(owner, uuid, name);
    }
    private static String[] lines(SignText text) {
        String[] lines = new String[4];
        for (int i = 0; i < lines.length; i++) lines[i] = text.getMessage(i, false).getString();
        return lines;
    }
    private static boolean container(BlockState state) {
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof AbstractFurnaceBlock || state.getBlock() instanceof DispenserBlock;
    }
    private static Set<BlockPos> chestHalves(ServerLevel level, BlockPos position) {
        Set<BlockPos> result = new LinkedHashSet<>(); result.add(position.immutable());
        BlockState state = level.getBlockState(position);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = position.relative(ChestBlock.getConnectedDirection(state));
            if (level.getBlockState(other).getBlock() == state.getBlock()) result.add(other);
        }
        return result;
    }
    private static void containerSigns(ServerLevel level, BlockPos position, Set<BlockPos> result) {
        for (BlockPos half : chestHalves(level, position)) {
            // Original WorldGuard standing signs sit immediately below either chest half.
            if (level.getBlockEntity(half.below()) instanceof SignBlockEntity) result.add(half.below());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos adjacent = half.relative(direction);
                if (level.getBlockEntity(adjacent) instanceof SignBlockEntity) result.add(adjacent);
            }
        }
    }
    private static Set<BlockPos> signsFor(ServerLevel level, BlockPos position) {
        Set<BlockPos> result = new LinkedHashSet<>();
        BlockState state = level.getBlockState(position);
        if (container(state)) containerSigns(level, position, result);
        if (level.getBlockEntity(position) instanceof SignBlockEntity) {
            result.add(position.immutable());
            BlockPos above = position.above();
            if (container(level.getBlockState(above))) containerSigns(level, above, result);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = position.relative(direction);
                if (container(level.getBlockState(neighbor))) containerSigns(level, neighbor, result);
            }
        }
        // A protected sign's supporting block is protected against indirect sign removal.
        if (level.getBlockState(position.above()).getBlock() instanceof StandingSignBlock) result.add(position.above());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = position.relative(direction);
            BlockState sign = level.getBlockState(adjacent);
            if (sign.getBlock() instanceof WallSignBlock && adjacent.relative(sign.getValue(WallSignBlock.FACING).getOpposite()).equals(position)) result.add(adjacent);
        }
        return result;
    }

    /** Runs immediately before vanilla applies filtered packet text to either sign face. */
    public static boolean mayEdit(ServerLevel level, BlockPos position, ServerPlayer player, boolean front, String[] proposed) {
        if (WorldGuardMod.service() == null) return true;
        boolean creatingLock = isLock(proposed);
        if (!enabled(level)) {
            if (creatingLock && !WorldGuardMod.service().config().bool(level.dimension().location().toString(), "chest-protection.disable-off-check", true)) {
                deny(player, "Sign chest protection is disabled."); return false;
            }
            return true;
        }
        if (!allows(level, position, player)) { deny(player, "This sign or adjacent chest is protected."); return false; }
        if (!creatingLock) return true;
        if (!bypass(player) && (proposed.length < 2 || !matchesOwner(proposed[1], player.getUUID(), player.getGameProfile().getName()))) {
            deny(player, "The first owner line must contain your name or UUID."); return false;
        }
        BlockState sign = level.getBlockState(position), below = level.getBlockState(position.below());
        if (sign.getBlock() instanceof StandingSignBlock && (below.getBlock() instanceof FallingBlock || below.getBlock() instanceof SignBlock || below.is(Blocks.TNT))) {
            deny(player, "The lock sign needs a solid, safe support."); return false;
        }
        return true;
    }
    private static void deny(ServerPlayer player, String message) {
        WorldGuardMod.service().deny(player, message);
    }

    @SubscribeEvent public void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getPlayer() instanceof ServerPlayer player
                && !allows(level, event.getPos(), player)) { event.setCanceled(true); deny(player, "break a protected chest or lock sign"); }
    }
    @SubscribeEvent public void access(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ServerPlayer player
                && !allows(level, event.getPos(), player)) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); deny(player, "access a protected chest or lock sign");
        }
    }
    @SubscribeEvent public void place(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player) || !enabled(level)) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiple) {
            for (var snapshot : multiple.getReplacedBlockSnapshots()) {
                if (container(snapshot.getCurrentState()) && !allows(level, snapshot.getPos(), player)) {
                    event.setCanceled(true); deny(player, "extend a protected chest"); return;
                }
            }
        } else if (container(event.getPlacedBlock()) && !allows(level, event.getPos(), player)) {
            event.setCanceled(true); deny(player, "extend a protected chest");
        }
    }
    @SubscribeEvent public void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && enabled(level)) event.getAffectedBlocks().removeIf(position -> !allows(level, position, null));
    }
    @SubscribeEvent public void piston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !enabled(level)) return;
        var helper = event.getStructureHelper();
        if (helper == null || !helper.resolve()) return;
        for (BlockPos position : helper.getToDestroy()) if (!allows(level, position, null)) { event.setCanceled(true); return; }
        for (BlockPos position : helper.getToPush()) if (!allows(level, position, null)) { event.setCanceled(true); return; }
    }
}
