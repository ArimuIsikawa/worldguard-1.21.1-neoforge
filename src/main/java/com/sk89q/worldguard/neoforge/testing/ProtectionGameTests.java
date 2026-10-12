package com.sk89q.worldguard.neoforge.testing;

import com.sk89q.worldguard.neoforge.ProtectionService;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import com.sk89q.worldguard.neoforge.WorldGuardConfig;
import com.sk89q.worldguard.neoforge.command.RegionCommands;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import com.sk89q.worldguard.neoforge.region.Region;
import com.sk89q.worldguard.neoforge.rules.SignChestProtection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.yaml.snakeyaml.Yaml;

/** Development-only registration is handled by NeoForge's GameTestHolder scanner. */
@GameTestHolder(WorldGuardMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProtectionGameTests {
    private ProtectionGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void membershipProtectsNativeBreakAndPlace(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "membership")) {
            BlockPos protectedPosition = fixture.pos(3, 2, 3);
            BlockPos placePosition = fixture.pos(4, 2, 3);
            helper.setBlock(3, 2, 3, Blocks.STONE);
            helper.setBlock(4, 1, 3, Blocks.STONE);
            ServerPlayer player = fixture.player(1, 2, 1);
            Region region = fixture.addRegion(fixture.pos(2, 1, 2), fixture.pos(5, 5, 5));

            var breakEvent = new BlockEvent.BreakEvent(fixture.level, protectedPosition,
                    fixture.level.getBlockState(protectedPosition), player);
            NeoForge.EVENT_BUS.post(breakEvent);
            check(breakEvent.isCanceled(), "Nonmember BreakEvent must be canceled");
            var placeEvent = new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(fixture.level.dimension(),
                    fixture.level, placePosition), Blocks.STONE.defaultBlockState(), player);
            NeoForge.EVENT_BUS.post(placeEvent);
            check(placeEvent.isCanceled(), "Nonmember EntityPlaceEvent must be canceled");

            check(!player.gameMode.destroyBlock(protectedPosition), "Vanilla destroyBlock must report denied breaking");
            check(fixture.level.getBlockState(protectedPosition).is(Blocks.STONE), "Denied native breaking must preserve the block");
            placeDirt(player, placePosition);
            check(fixture.level.getBlockState(placePosition).isAir(), "Denied native ItemStack placement must roll back");

            region.members().uuids().add(player.getUUID());
            fixture.save();
            var memberBreak = new BlockEvent.BreakEvent(fixture.level, protectedPosition,
                    fixture.level.getBlockState(protectedPosition), player);
            NeoForge.EVENT_BUS.post(memberBreak);
            check(!memberBreak.isCanceled(), "Member BreakEvent must be allowed");
            var memberPlace = new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(fixture.level.dimension(),
                    fixture.level, placePosition), Blocks.STONE.defaultBlockState(), player);
            NeoForge.EVENT_BUS.post(memberPlace);
            check(!memberPlace.isCanceled(), "Member EntityPlaceEvent must be allowed");
            check(player.gameMode.destroyBlock(protectedPosition), "A member must be able to break a block through vanilla game mode");
            check(fixture.level.getBlockState(protectedPosition).isAir(), "Allowed native breaking must remove the block");
            placeDirt(player, placePosition);
            check(fixture.level.getBlockState(placePosition).is(Blocks.DIRT), "Allowed native ItemStack placement must remain in the world");
        }
        helper.succeed();
    }

    private static void placeDirt(ServerPlayer player, BlockPos destination) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.DIRT));
        BlockPos against = destination.below();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(against).add(0, .5, 0), Direction.UP, against, false);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void projectileOwnerCannotBypassPvp(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "pvp")) {
            ServerPlayer shooter = fixture.player(1, 2, 1);
            ServerPlayer target = fixture.player(4, 2, 4);
            Region region = fixture.addRegion(fixture.pos(3, 1, 3), fixture.pos(5, 5, 5));
            region.flags().put("pvp", "deny");
            fixture.save();
            Arrow arrow = new Arrow(EntityType.ARROW, fixture.level);
            arrow.setOwner(shooter);
            // A missing damage-source owner still has to resolve the projectile's owner.
            var source = fixture.level.damageSources().arrow(arrow, null);
            var denied = new LivingIncomingDamageEvent(target, new DamageContainer(source, 4));
            NeoForge.EVENT_BUS.post(denied);
            check(denied.isCanceled(), "PvP denial must attribute an arrow to its player owner");
            region.flags().put("pvp", "allow");
            fixture.save();
            var allowed = new LivingIncomingDamageEvent(target, new DamageContainer(source, 4));
            NeoForge.EVENT_BUS.post(allowed);
            check(!allowed.isCanceled(), "PvP allow must permit the same attributed projectile damage");
            arrow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void explosionFiltersBlocksAndEntities(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "explosion")) {
            BlockPos protectedPosition = fixture.pos(4, 2, 4);
            BlockPos unprotectedPosition = fixture.pos(1, 2, 1);
            ServerPlayer target = fixture.player(4, 2, 4);
            ServerPlayer outside = fixture.player(1, 2, 1);
            Region region = fixture.addRegion(fixture.pos(3, 1, 3), fixture.pos(5, 5, 5));
            region.flags().put("tnt", "deny");
            fixture.save();
            PrimedTnt tnt = new PrimedTnt(fixture.level, unprotectedPosition.getX(), unprotectedPosition.getY(),
                    unprotectedPosition.getZ(), outside);
            Explosion explosion = new Explosion(fixture.level, tnt, unprotectedPosition.getX(), unprotectedPosition.getY(),
                    unprotectedPosition.getZ(), 4, false, Explosion.BlockInteraction.DESTROY,
                    List.of(protectedPosition, unprotectedPosition));
            var event = new ExplosionEvent.Detonate(fixture.level, explosion, new ArrayList<>(List.of(target, outside)));
            NeoForge.EVENT_BUS.post(event);
            check(!event.getAffectedBlocks().contains(protectedPosition), "Denied TNT must remove protected blocks from the explosion");
            check(event.getAffectedBlocks().contains(unprotectedPosition), "Unprotected explosion blocks must remain");
            check(!event.getAffectedEntities().contains(target), "Denied TNT must remove protected entities from the explosion");
            check(event.getAffectedEntities().contains(outside), "Unprotected explosion entities must remain");

            region.flags().remove("tnt");
            fixture.save();
            var exterior = explosionEvent(fixture, tnt, unprotectedPosition, protectedPosition, target, outside);
            NeoForge.EVENT_BUS.post(exterior);
            check(!exterior.getAffectedBlocks().contains(protectedPosition),
                    "An outside nonmember's TNT must respect a claim's default build protection");
            check(exterior.getAffectedEntities().contains(target),
                    "Default build protection must not silently deny otherwise allowed entity damage");
            region.flags().put("tnt", "allow");
            fixture.save();
            var permitted = explosionEvent(fixture, tnt, unprotectedPosition, protectedPosition, target, outside);
            NeoForge.EVENT_BUS.post(permitted);
            check(permitted.getAffectedBlocks().contains(protectedPosition),
                    "Explicit TNT allow must permit explosion block damage through the default build protection");
            region.flags().put("block-break", "deny");
            fixture.save();
            var blockDenied = explosionEvent(fixture, tnt, unprotectedPosition, protectedPosition, target, outside);
            NeoForge.EVENT_BUS.post(blockDenied);
            check(!blockDenied.getAffectedBlocks().contains(protectedPosition),
                    "Explicit block-break deny must take precedence over TNT allow");
            tnt.discard();
        }
        helper.succeed();
    }

    private static ExplosionEvent.Detonate explosionEvent(Fixture fixture, PrimedTnt tnt, BlockPos origin,
                                                          BlockPos protectedPosition, ServerPlayer target, ServerPlayer outside) {
        Explosion explosion = new Explosion(fixture.level, tnt, origin.getX(), origin.getY(), origin.getZ(),
                4, false, Explosion.BlockInteraction.DESTROY, List.of(protectedPosition, origin));
        return new ExplosionEvent.Detonate(fixture.level, explosion, new ArrayList<>(List.of(target, outside)));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pistonCannotPushAcrossClaimBoundary(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "piston")) {
            BlockPos piston = fixture.pos(2, 2, 3);
            fixture.level.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), 2);
            fixture.level.setBlock(piston.east(), Blocks.STONE.defaultBlockState(), 2);
            Region region = fixture.addRegion(piston.east(2), piston.east(2).above());
            var denied = new PistonEvent.Pre(fixture.level, piston, Direction.EAST, PistonEvent.PistonMoveType.EXTEND);
            check(denied.getStructureHelper() != null && denied.getStructureHelper().resolve(), "Test piston must have a movable structure");
            NeoForge.EVENT_BUS.post(denied);
            check(denied.isCanceled(), "A piston outside a claim must not push a block inside the claim");
            region.flags().put("build", "allow");
            fixture.save();
            var allowed = new PistonEvent.Pre(fixture.level, piston, Direction.EAST, PistonEvent.PistonMoveType.EXTEND);
            NeoForge.EVENT_BUS.post(allowed);
            check(!allowed.isCanceled(), "An explicitly build-allow destination must permit the same piston movement");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fluidChecksDestinationAndNativeMutationsAreGuarded(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "fluid")) {
            BlockPos source = fixture.pos(3, 2, 3);
            BlockPos destination = source.east();
            for (int x = 2; x <= 5; x++) {
                for (int z = 2; z <= 4; z++) {
                    helper.setBlock(x, 1, z, Blocks.STONE);
                }
            }
            fixture.level.setBlock(source.west(), Blocks.STONE.defaultBlockState(), 2);
            fixture.level.setBlock(source.north(), Blocks.STONE.defaultBlockState(), 2);
            fixture.level.setBlock(source.south(), Blocks.STONE.defaultBlockState(), 2);
            fixture.level.setBlock(destination, Blocks.AIR.defaultBlockState(), 2);
            fixture.level.setBlock(source, Blocks.WATER.defaultBlockState(), 2);
            Region region = fixture.addRegion(destination, destination.above());
            region.flags().put("water-flow", "deny");
            fixture.save();
            Fluids.WATER.tick(fixture.level, source, fixture.level.getFluidState(source));
            check(fixture.level.getFluidState(destination).isEmpty(), "Water-flow denial must apply at the destination outside the source claim");
            region.flags().put("water-flow", "allow");
            fixture.save();
            Fluids.WATER.tick(fixture.level, source, fixture.level.getFluidState(source));
            check(fixture.level.getFluidState(destination).isEmpty(),
                    "Water-flow allow alone must preserve ownership boundary protection with the default liquid-flow configuration");
            region.flags().put("build", "allow");
            fixture.save();
            Fluids.WATER.tick(fixture.level, source, fixture.level.getFluidState(source));
            check(fixture.level.getFluidState(destination).is(FluidTags.WATER),
                    "The same water source must flow after both its destination flag and build boundary allow it");

            fixture.level.setBlock(destination, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 7)
                    .setValue(LeavesBlock.PERSISTENT, false), 2);
            region.flags().put("leaf-decay", "deny");
            fixture.save();
            try (var ignored = ProtectionHooks.scope("leaf-decay", null)) {
                check(!fixture.level.removeBlock(destination, false), "Native Level removal must reject a denied natural mutation");
            }
            check(fixture.level.getBlockState(destination).is(Blocks.OAK_LEAVES), "Denied natural mutation must preserve leaves");
            region.flags().put("leaf-decay", "allow");
            fixture.save();
            try (var ignored = ProtectionHooks.scope("leaf-decay", null)) {
                check(fixture.level.removeBlock(destination, false), "Allowed natural mutation must perform native Level removal");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void commandAndYamlRoundTrip(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "commands")) {
            ServerPlayer player = fixture.player(1, 2, 1);
            CommandSourceStack source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            BlockPos min = fixture.pos(2, 1, 2), max = fixture.pos(5, 5, 5);
            fixture.command(source, "rg pos1 " + coordinates(min));
            fixture.command(source, "rg pos2 " + coordinates(max));
            fixture.command(source, "rg define " + fixture.id);
            fixture.ids.add(fixture.id);
            fixture.command(source, "rg flag " + fixture.id + " pvp -g nonmembers deny");
            fixture.command(source, "rg flag " + fixture.id + " pvp -g members");
            check("deny".equals(fixture.regions.get(fixture.id).flags().get("pvp"))
                    && "members".equals(fixture.regions.get(fixture.id).flags().get("pvp-group")),
                    "A group-only flag command must preserve the state value");
            fixture.command(source, "rg flag " + fixture.id + " pvp");
            check(!fixture.regions.get(fixture.id).flags().containsKey("pvp")
                    && !fixture.regions.get(fixture.id).flags().containsKey("pvp-group"),
                    "Clearing a flag must also clear its paired group override");
            fixture.command(source, "rg flag " + fixture.id + " pvp -g nonmembers deny");
            fixture.command(source, "rg priority " + fixture.id + " 7");
            fixture.command(source, "rg addmember " + fixture.id + " " + player.getUUID());
            Region defined = fixture.regions.get(fixture.id);
            check(defined != null && defined.minX() == min.getX() && defined.maxZ() == max.getZ(), "Brigadier define must use the native selection");
            check("deny".equals(defined.flags().get("pvp")) && "nonmembers".equals(defined.flags().get("pvp-group")),
                    "Brigadier flags must store both state and region group");
            check(defined.priority() == 7 && defined.members().uuids().contains(player.getUUID()), "Brigadier metadata and domain changes must persist");
            fixture.service.repository().reload(fixture.dimension);
            Region loaded = fixture.regions.get(fixture.id);
            check(loaded != null && loaded.priority() == 7 && loaded.members().uuids().contains(player.getUUID())
                    && "deny".equals(loaded.flags().get("pvp")), "Saved command state must round-trip through WorldGuard YAML");

            String importedId = fixture.id + "_import";
            fixture.ids.add(importedId);
            Path importFile = fixture.service.directory().resolve("import").resolve(fixture.id + ".yml");
            fixture.files.add(importFile);
            Files.writeString(importFile, "regions:\n  " + importedId + ":\n    type: cuboid\n"
                    + "    min: {x: " + min.getX() + ", y: " + min.getY() + ", z: " + min.getZ() + "}\n"
                    + "    max: {x: " + max.getX() + ", y: " + max.getY() + ", z: " + max.getZ() + "}\n"
                    + "    priority: 13\n    parent: " + fixture.id + "\n    flags: {chest-access: deny}\n"
                    + "    owners: {unique-ids: ['" + player.getUUID() + "']}\n    members: {}\n"
                    // A WorldGuard region file must contain its own parent definitions.
                    + "  " + fixture.id + ":\n    type: cuboid\n"
                    + "    min: {x: " + min.getX() + ", y: " + min.getY() + ", z: " + min.getZ() + "}\n"
                    + "    max: {x: " + max.getX() + ", y: " + max.getY() + ", z: " + max.getZ() + "}\n"
                    + "    priority: 99\n    flags: {}\n    owners: {}\n    members: {}\n");
            fixture.command(source, "rg import " + importFile.getFileName());
            Region imported = fixture.regions.get(importedId);
            check(imported != null && imported.priority() == 13 && fixture.id.equals(imported.parent())
                    && imported.owners().uuids().contains(player.getUUID()) && "deny".equals(imported.flags().get("chest-access")),
                    "Brigadier import must preserve legacy YAML geometry, flags, domains and parents");
            check(fixture.regions.get(fixture.id).priority() == 7,
                    "A merging YAML import must preserve an existing region with the same ID");
            fixture.service.repository().reload(fixture.dimension);
            check(fixture.regions.containsKey(importedId), "Imported regions must be saved immediately");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeTravelRespectsGroupsAndRestoresGameMode(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "travel")) {
            ServerPlayer player = fixture.player(1, 2, 1);
            player.setGameMode(GameType.SURVIVAL);
            BlockPos outside = player.blockPosition();
            BlockPos inside = fixture.pos(4, 2, 4);
            Region region = fixture.addRegion(fixture.pos(3, 1, 3), fixture.pos(5, 5, 5));
            region.flags().put("entry", "deny"); // Default group is nonmembers.
            region.flags().put("game-mode", "adventure");
            region.flags().put("game-mode-group", "members");
            fixture.save();
            player.teleportTo(fixture.level, inside.getX() + .5, inside.getY(), inside.getZ() + .5, 0, 0);
            check(player.blockPosition().equals(outside), "Native teleport must reject entry for a nonmember");
            var changed = player.changeDimension(new DimensionTransition(fixture.level,
                    Vec3.atBottomCenterOf(inside), Vec3.ZERO, 0, 0, DimensionTransition.DO_NOTHING));
            check(changed == null && player.blockPosition().equals(outside),
                    "Native changeDimension must also reject entry for a nonmember");
            fixture.service.sessions().tick(player);
            check(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                    "A members-only game mode flag must not apply to a nonmember outside the region");

            region.members().uuids().add(player.getUUID());
            fixture.save();
            player.teleportTo(fixture.level, inside.getX() + .5, inside.getY(), inside.getZ() + .5, 0, 0);
            check(player.blockPosition().equals(inside), "Default nonmembers entry group must allow a region member");
            fixture.service.sessions().tick(player);
            check(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                    "Session tick must apply a members-only game mode flag to the member");

            region.flags().put("exit", "deny");
            region.flags().put("exit-group", "members");
            region.flags().put("exit-via-teleport", "deny");
            fixture.save();
            player.teleportTo(fixture.level, outside.getX() + .5, outside.getY(), outside.getZ() + .5, 0, 0);
            check(player.blockPosition().equals(inside), "Members-only exit denial must prevent native teleport exit");
            region.flags().put("exit-group", "nonmembers");
            fixture.save();
            player.teleportTo(fixture.level, outside.getX() + .5, outside.getY(), outside.getZ() + .5, 0, 0);
            check(player.blockPosition().equals(outside), "A member must leave when exit denial applies only to nonmembers");
            fixture.service.sessions().tick(player);
            check(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                    "Leaving the region must restore the player's original game mode");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void optionalWorldEditCuboidAndPolygonRoundTrip(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("worldedit")) {
            WorldGuardMod.LOGGER.info("Skipping optional WorldEdit round-trip branch: WorldEdit is not installed");
            helper.succeed();
            return;
        }
        try (Fixture fixture = new Fixture(helper, "worldedit")) {
            ServerPlayer player = fixture.player(1, 2, 1);
            CommandSourceStack source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            BlockPos min = fixture.pos(2, 1, 2), max = fixture.pos(5, 5, 5);
            fixture.command(source, "rg pos1 " + coordinates(min));
            fixture.command(source, "rg pos2 " + coordinates(max));
            fixture.command(source, "rg define " + fixture.id);
            fixture.ids.add(fixture.id);
            fixture.command(source, "rg select " + fixture.id);
            fixture.command(source, "rg selection clear");
            fixture.command(source, "rg redefine " + fixture.id);
            Region cuboid = fixture.regions.get(fixture.id);
            check(cuboid != null && cuboid.type().equals("cuboid") && cuboid.minX() == min.getX()
                    && cuboid.minY() == min.getY() && cuboid.minZ() == min.getZ()
                    && cuboid.maxX() == max.getX() && cuboid.maxY() == max.getY() && cuboid.maxZ() == max.getZ(),
                    "WorldEdit cuboid selection must survive select, native clear and redefine");

            String polygonId = fixture.id + "_poly";
            fixture.command(source, "rg selection clear");
            List<Region.Point> points = List.of(new Region.Point(min.getX(), min.getZ()),
                    new Region.Point(max.getX(), min.getZ()), new Region.Point(max.getX(), max.getZ()),
                    new Region.Point(min.getX() + 1, max.getZ()));
            for (Region.Point point : points) {
                fixture.command(source, "rg point " + point.x() + " " + min.getY() + " " + point.z());
            }
            fixture.command(source, "rg heights " + min.getY() + " " + max.getY());
            fixture.command(source, "rg define " + polygonId);
            fixture.ids.add(polygonId);
            fixture.command(source, "rg select " + polygonId);
            fixture.command(source, "rg selection clear");
            fixture.command(source, "rg redefine " + polygonId);
            Region polygon = fixture.regions.get(polygonId);
            check(polygon != null && polygon.type().equals("poly2d") && polygon.points().equals(points)
                    && polygon.minY() == min.getY() && polygon.maxY() == max.getY(),
                    "WorldEdit polygon selection must preserve all points and heights through select, native clear and redefine");
            WorldGuardMod.LOGGER.info("Optional WorldEdit cuboid and polygon selection round-trip passed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hungerRegenerationAndVisualPacketsRespectFlags(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "food_visuals")) {
            ServerPlayer player = fixture.player(4, 2, 4);
            Region region = fixture.addRegion(fixture.pos(3, 1, 3), fixture.pos(5, 5, 5));
            region.flags().put("natural-health-regen", "deny");
            region.flags().put("natural-hunger-drain", "deny");
            region.flags().put("weather-lock", "clear");
            region.flags().put("time-lock", "6000");
            fixture.save();
            player.setHealth(10);
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(6);
            player.getFoodData().setExhaustion(0);
            for (int tick = 0; tick < 20; tick++) {
                player.getFoodData().tick(player);
            }
            check(player.getHealth() == 10, "FoodData native regeneration must obey natural-health-regen deny");
            region.flags().put("natural-health-regen", "allow");
            fixture.save();
            for (int tick = 0; tick < 10; tick++) {
                player.getFoodData().tick(player);
            }
            check(player.getHealth() > 10, "The same native FoodData regeneration must heal after allowing the flag");

            region.flags().put("natural-health-regen", "deny");
            fixture.save();
            player.getFoodData().setFoodLevel(19);
            player.getFoodData().setSaturation(5);
            player.getFoodData().setExhaustion(6);
            player.getFoodData().tick(player);
            check(player.getFoodData().getFoodLevel() == 19 && player.getFoodData().getSaturationLevel() == 5,
                    "Native hunger drain denial must preserve hunger and saturation");
            region.flags().put("natural-hunger-drain", "allow");
            fixture.save();
            player.getFoodData().setExhaustion(6);
            player.getFoodData().tick(player);
            check(player.getFoodData().getSaturationLevel() == 4,
                    "Native hunger drain must consume saturation after allowing the flag");

            var timePacket = new ClientboundSetTimePacket(fixture.level.getGameTime(), fixture.level.getDayTime(), true);
            var lockedTime = (ClientboundSetTimePacket) fixture.service.sessions().transformPacket(player, timePacket);
            check(lockedTime.getDayTime() == -6000, "Absolute regional time must transform to the frozen midday packet convention");
            var rainPacket = new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0);
            var lockedRain = (ClientboundGameEventPacket) fixture.service.sessions().transformPacket(player, rainPacket);
            check(lockedRain.getEvent() == ClientboundGameEventPacket.STOP_RAINING,
                    "Weather clear must transform incoming rain notifications");
            var thunder = new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 1);
            check(((ClientboundGameEventPacket) fixture.service.sessions().transformPacket(player, thunder)).getParam() == 0,
                    "Weather clear must transform the thunder intensity to zero");
            region.flags().put("time-lock", "+1000");
            fixture.save();
            check(((ClientboundSetTimePacket) fixture.service.sessions().transformPacket(player, timePacket)).getDayTime()
                    == fixture.level.getDayTime() + 1000, "Relative time must preserve the world clock and add the region offset");

            BlockPos outside = fixture.pos(1, 2, 1);
            player.setPos(outside.getX() + .5, outside.getY(), outside.getZ() + .5);
            check(fixture.service.sessions().transformPacket(player, timePacket) == timePacket
                            && fixture.service.sessions().transformPacket(player, rainPacket) == rainPacket,
                    "Outside the region, the original world time and weather packets must pass through");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void signChestProtectsOwnerAccessRemovalAndAutomation(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "sign_chest");
             ChestConfiguration configuration = new ChestConfiguration(fixture.service, fixture.dimension)) {
            ServerPlayer owner = fixture.player(1, 2, 1);
            ServerPlayer outsider = fixture.player(2, 2, 1);
            BlockPos chest = fixture.pos(4, 3, 4);
            BlockPos signPosition = chest.below();
            BlockPos support = chest.below(2);
            fixture.level.setBlock(support, Blocks.STONE.defaultBlockState(), 2);
            fixture.level.setBlock(signPosition, Blocks.OAK_SIGN.defaultBlockState(), 2);
            fixture.level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
            check(fixture.level.getBlockEntity(signPosition) instanceof SignBlockEntity,
                    "The original below-chest sign layout must create a sign block entity");
            SignBlockEntity sign = (SignBlockEntity) fixture.level.getBlockEntity(signPosition);
            sign.setText(sign.getFrontText().setMessage(0, Component.literal("[Lock]"))
                    .setMessage(1, Component.literal("uuid:" + owner.getUUID())), true);

            check(SignChestProtection.allows(fixture.level, chest, owner), "A sign's UUID owner must be able to access the chest");
            check(!SignChestProtection.allows(fixture.level, chest, outsider), "The lock must deny a different UUID even for identical mock names");
            check(!SignChestProtection.allows(fixture.level, chest, null), "Locked chest automation must be denied without an owner");
            check(!ProtectionHooks.inventory(fixture.level, chest, chest), "The native hopper hook must reject a locked chest");
            check(!SignChestProtection.allows(fixture.level, signPosition, outsider)
                            && !SignChestProtection.allows(fixture.level, support, outsider),
                    "The lock sign and its support must be protected together with the chest");

            var deniedAccess = new PlayerInteractEvent.RightClickBlock(outsider, InteractionHand.MAIN_HAND, chest,
                    new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false));
            NeoForge.EVENT_BUS.post(deniedAccess);
            check(deniedAccess.isCanceled(), "The native interaction event must deny a nonowner's chest access");
            var ownerAccess = new PlayerInteractEvent.RightClickBlock(owner, InteractionHand.MAIN_HAND, chest,
                    new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false));
            NeoForge.EVENT_BUS.post(ownerAccess);
            check(!ownerAccess.isCanceled(), "The same native interaction must allow the owner");
            check(!outsider.gameMode.destroyBlock(chest) && fixture.level.getBlockState(chest).is(Blocks.CHEST),
                    "Denied native breaking must retain the locked chest");
            check(!outsider.gameMode.destroyBlock(support) && fixture.level.getBlockState(support).is(Blocks.STONE)
                            && fixture.level.getBlockEntity(signPosition) instanceof SignBlockEntity,
                    "Denied support breaking must retain both the supporting block and the lock sign");
            check(!SignChestProtection.mayEdit(fixture.level, signPosition, outsider, true, new String[]{"", "", "", ""}),
                    "A nonowner must not erase a lock by editing its sign");
            check(SignChestProtection.mayEdit(fixture.level, signPosition, owner, true, new String[]{"", "", "", ""}),
                    "The owner must be allowed to remove their lock by editing the sign");

            configuration.enabled(false);
            check(SignChestProtection.allows(fixture.level, chest, outsider)
                            && SignChestProtection.allows(fixture.level, chest, null),
                    "Disabling sign protection must allow both players and automation");
            check(outsider.gameMode.destroyBlock(chest) && fixture.level.getBlockState(chest).isAir(),
                    "After disabling sign protection the same native break must remove the chest");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void migratedGlobalRulesAffectNativeGameplay(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "global_rules");
             ChestConfiguration configuration = new ChestConfiguration(fixture.service, null)) {
            ServerPlayer player = fixture.player(1, 2, 1);
            player.setGameMode(GameType.SURVIVAL);
            BlockPos stone = fixture.pos(6, 2, 6);
            ItemStack tool = new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
            tool.setDamageValue(120);
            configuration.set("protection.item-durability", false);
            tool.mineBlock(fixture.level, Blocks.STONE.defaultBlockState(), stone, player);
            check(tool.getDamageValue() == 0, "Disabling item durability must repair and preserve a native mining tool");
            configuration.set("protection.item-durability", true);
            tool.mineBlock(fixture.level, Blocks.STONE.defaultBlockState(), stone, player);
            check(tool.getDamageValue() > 0, "Enabling durability must restore normal mining wear");

            BlockPos eggs = fixture.pos(6, 2, 5);
            fixture.level.setBlock(eggs, Blocks.TURTLE_EGG.defaultBlockState(), 2);
            var destroyEgg = net.minecraft.world.level.block.TurtleEggBlock.class.getDeclaredMethod("destroyEgg",
                    net.minecraft.world.level.Level.class, net.minecraft.world.level.block.state.BlockState.class,
                    BlockPos.class, net.minecraft.world.entity.Entity.class, int.class);
            destroyEgg.setAccessible(true);
            configuration.set("turtle-egg.disable-player-trampling", true);
            destroyEgg.invoke(Blocks.TURTLE_EGG, fixture.level, fixture.level.getBlockState(eggs), eggs, player, 1);
            check(fixture.level.getBlockState(eggs).is(Blocks.TURTLE_EGG), "The actual turtle egg method must retain eggs when trampling is disabled");
            configuration.set("turtle-egg.disable-player-trampling", false);
            destroyEgg.invoke(Blocks.TURTLE_EGG, fixture.level, fixture.level.getBlockState(eggs), eggs, player, 1);
            check(fixture.level.getBlockState(eggs).isAir(), "The same actual egg method must destroy eggs when trampling is enabled");

            configuration.set("event-handling.interaction-whitelist", List.of("minecraft:lever"));
            BlockPos lever = fixture.pos(4, 2, 4);
            fixture.level.setBlock(lever, Blocks.LEVER.defaultBlockState(), 2);
            Region region = fixture.addRegion(fixture.pos(3, 1, 3), fixture.pos(5, 5, 5));
            region.flags().put("interact", "deny"); fixture.save();
            var allowedUse = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, lever,
                    new BlockHitResult(Vec3.atCenterOf(lever), Direction.UP, lever, false));
            NeoForge.EVENT_BUS.post(allowedUse);
            check(!allowedUse.isCanceled(), "An explicitly whitelisted lever must permit interaction in a protected claim");
            configuration.set("event-handling.interaction-whitelist", List.of());
            var deniedUse = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, lever,
                    new BlockHitResult(Vec3.atCenterOf(lever), Direction.UP, lever, false));
            NeoForge.EVENT_BUS.post(deniedUse);
            check(deniedUse.isCanceled(), "Removing the whitelist must restore region interaction denial");

            configuration.set("security.block-in-game-op-command", true);
            var dispatcher = fixture.level.getServer().getCommands().getDispatcher();
            var opEvent = new net.neoforged.neoforge.event.CommandEvent(dispatcher.parse("op test-mock-player", player.createCommandSourceStack().withPermission(4)));
            NeoForge.EVENT_BUS.post(opEvent);
            check(opEvent.isCanceled(), "The global security rule must deny an in-game operator command");
            var consoleOp = new net.neoforged.neoforge.event.CommandEvent(dispatcher.parse("op test-mock-player", fixture.level.getServer().createCommandSourceStack()));
            NeoForge.EVENT_BUS.post(consoleOp);
            check(!consoleOp.isCanceled(), "The same security rule must preserve console operator commands");
            configuration.set("host-keys", Map.of(player.getUUID().toString(), "private.example"));
            var connection = player.connection.getConnection();
            com.sk89q.worldguard.neoforge.rules.HostKeyService.handshake(connection, "wrong.example");
            check(!com.sk89q.worldguard.neoforge.rules.HostKeyService.allows(connection, player.getGameProfile()), "The native host key service must reject an incorrect handshake host");
            com.sk89q.worldguard.neoforge.rules.HostKeyService.handshake(connection, "private.example");
            check(com.sk89q.worldguard.neoforge.rules.HostKeyService.allows(connection, player.getGameProfile()), "The native host key service must accept the configured host");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void claimLimitsAndLocationBoundsAreApplied(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "claim_rules");
             ChestConfiguration configuration = new ChestConfiguration(fixture.service, fixture.dimension)) {
            ServerPlayer player = fixture.player(1, 2, 1);
            CommandSourceStack source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            BlockPos min = fixture.pos(3, 1, 3), max = fixture.pos(5, 4, 5);
            fixture.command(source, "rg pos1 " + coordinates(min));
            fixture.command(source, "rg pos2 " + coordinates(max));
            configuration.set("regions.max-claim-volume", 1);
            fixture.rejectedCommand(source, "rg claim " + fixture.id);
            check(!fixture.regions.containsKey(fixture.id), "An oversized native claim must fail without changing saved regions");
            configuration.set("regions.max-claim-volume", 1000);
            configuration.set("regions.set-parent-on-claim", fixture.id + "_parent");
            Region parent = Region.cuboid(fixture.id + "_parent", min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
            parent.owners().uuids().add(player.getUUID()); fixture.regions.put(parent.id(), parent); fixture.ids.add(parent.id()); fixture.save();
            configuration.set("regions.claim-only-inside-existing-regions", true);
            fixture.command(source, "rg claim " + fixture.id); fixture.ids.add(fixture.id);
            check(fixture.regions.get(fixture.id).parent().equals(parent.id()), "Claiming must apply its configured parent");
            configuration.set("regions.location-flags-only-inside-regions", true);
            BlockPos outside = fixture.pos(7, 2, 7);
            String badLocation = outside.getX() + "," + outside.getY() + "," + outside.getZ();
            fixture.rejectedCommand(source, "rg flag " + fixture.id + " teleport " + badLocation);
            fixture.command(source, "rg flag " + fixture.id + " teleport " + min.getX() + "," + min.getY() + "," + min.getZ());
            fixture.regions.get(fixture.id).flags().put("invincible", "allow"); fixture.save();
            player.setPos(min.getX() + .5, min.getY(), min.getZ() + .5);
            fixture.service.god(player, true);
            fixture.command(source, "slay");
            check(player.isDeadOrDying(), "Authorized slay must work even inside an invincible region");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pairedPortalPlansDoNotPartiallyModifyProtectedClaims(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper,"portal_plan")) {
            BlockPos origin=fixture.pos(3,2,3);
            for (int x=0;x<8;x++) for (int z=0;z<8;z++) fixture.level.setBlock(fixture.pos(x,1,z),Blocks.STONE.defaultBlockState(),2);
            Region region=fixture.addRegion(origin.offset(-20,-200,-20),origin.offset(20,400,20));
            var denied=fixture.level.getPortalForcer().createPortal(origin,Direction.Axis.X);
            check(denied.isEmpty(),"Portal creation must fail when its complete frame would enter a protected claim");
            check(fixture.level.getBlockState(origin).isAir(),"Planning a denied portal must leave the existing world untouched");
            region.flags().put("block-break","allow"); region.flags().put("block-place","allow"); fixture.save();
            var allowed=fixture.level.getPortalForcer().createPortal(origin,Direction.Axis.X);
            check(allowed.isPresent(),"Explicit block break and placement allowances must permit the same native portal");
            check(fixture.level.getBlockState(allowed.get().minCorner).is(Blocks.NETHER_PORTAL),"A permitted portal must actually commit its interior blocks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeSqlStorageSwitchAndMigrationRetainProtection(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper,"sql_native"); ChestConfiguration configuration = new ChestConfiguration(fixture.service,null)) {
            BlockPos position=fixture.pos(4,2,4); ServerPlayer player=fixture.player(1,2,1);
            fixture.addRegion(fixture.pos(3,1,3),fixture.pos(5,5,5));
            Path database=fixture.service.directory().resolve("import").resolve(fixture.id + ".sqlite"); fixture.files.add(database);
            String dsn="jdbc:sqlite:" + database.toAbsolutePath();
            var settings=new com.sk89q.worldguard.neoforge.storage.JdbcSettings(dsn,"","","");
            new com.sk89q.worldguard.neoforge.storage.SqlRegionStorage(settings,fixture.service.config().worldAliases()).save(fixture.dimension,fixture.regions);
            configuration.set("regions.sql.dsn",dsn); configuration.set("regions.sql.use",true);
            check(fixture.service.repository().isSql(),"Configuration reload must enable the actual native SQL backend");
            check(!fixture.service.canUse(fixture.level,position,player,"block-break"),"The same loaded claim must remain protected after switching storage");
            fixture.command(player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),"rg flag " + fixture.id + " pvp deny");
            var stored=new com.sk89q.worldguard.neoforge.storage.SqlRegionStorage(settings,fixture.service.config().worldAliases()).load(fixture.dimension);
            check("deny".equals(stored.get(fixture.id).flags().get("pvp")),"A native region command must save its actual flag to SQL");
            check(fixture.service.migrateDatabase("sql","yaml") >= 1,"The native migration command service must copy SQL regions back to YAML");
            configuration.set("regions.sql.use",false);
            check(!fixture.service.repository().isSql() && "deny".equals(fixture.regions.get(fixture.id).flags().get("pvp")),"The migrated flag must survive switching back to YAML");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void configuredHopperMovesAreDeniedPreservedIgnoredOrBroken(GameTestHelper helper) throws Exception {
        try (Fixture fixture=new Fixture(helper,"hopper_options"); ChestConfiguration configuration=new ChestConfiguration(fixture.service,fixture.dimension)) {
            BlockPos position=fixture.pos(3,2,3), chest=position.east();
            fixture.level.setBlock(position,Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING,Direction.EAST),2);
            fixture.level.setBlock(chest,Blocks.CHEST.defaultBlockState(),2);
            fixture.addRegion(chest,chest.above());
            var hopper=(net.minecraft.world.level.block.entity.HopperBlockEntity)fixture.level.getBlockEntity(position);
            hopper.setItem(0,new ItemStack(net.minecraft.world.item.Items.DIRT,4));
            var eject=net.minecraft.world.level.block.entity.HopperBlockEntity.class.getDeclaredMethod("ejectItems",net.minecraft.world.level.Level.class,BlockPos.class,net.minecraft.world.level.block.entity.HopperBlockEntity.class);
            eject.setAccessible(true);
            configuration.set("event-handling.break-hoppers-on-denied-move",false);
            check(!((Boolean)eject.invoke(null,fixture.level,position,hopper)),"The real hopper transfer must be denied across a claimed boundary");
            fixture.service.processDeniedHoppers();
            check(fixture.level.getBlockState(position).is(Blocks.HOPPER) && hopper.getItem(0).getCount()==4,"Disabling hopper break must preserve both hopper and inventory");
            configuration.set("event-handling.ignore-hopper-item-move-events",true);
            check((Boolean)eject.invoke(null,fixture.level,position,hopper),"The ignore option must permit the same native hopper transfer");
            check(hopper.getItem(0).getCount()==3,"Only the permitted native transfer may consume an item");
            configuration.set("event-handling.ignore-hopper-item-move-events",false);
            configuration.set("event-handling.break-hoppers-on-denied-move",true);
            check(!((Boolean)eject.invoke(null,fixture.level,position,hopper)),"Re-enabling checks must deny the next transfer");
            fixture.service.processDeniedHoppers();
            check(fixture.level.getBlockState(position).isAir(),"The break-on-denial option must remove the blocked hopper on the server tick");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeGroupProvidersGrantInheritedMembership(GameTestHelper helper) throws Exception {
        try(Fixture fixture=new Fixture(helper,"native_group")) {
            ServerPlayer player=fixture.player(4,2,4); Region region=fixture.addRegion(fixture.pos(3,1,3),fixture.pos(5,5,5));
            region.owners().groups().add("wgtest-builders"); fixture.save();
            check(!fixture.service.canBuild(fixture.level,player.blockPosition(),player),"An unresolved legacy group must not grant access");
            TestGroupProvider provider=new TestGroupProvider(player.getUUID()); NeoForge.EVENT_BUS.register(provider);
            try {
                fixture.service.reload();
                check(fixture.service.canBuild(fixture.level,player.blockPosition(),player),"A registered native group provider must grant owner access");
            } finally { NeoForge.EVENT_BUS.unregister(provider); fixture.service.reload(); }
            check(!fixture.service.canBuild(fixture.level,player.blockPosition(),player),"Removing the group provider must revoke the granted membership");
        }
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void originalCommandOptionsRunThroughNativeCommandEvents(GameTestHelper helper) throws Exception {
        try(Fixture fixture=new Fixture(helper,"legacy_native")) {
            ServerPlayer player=fixture.player(2,2,2); var source=player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            String parent=fixture.id+"_template", child=fixture.id+"_child"; fixture.ids.add(parent); fixture.ids.add(child);
            fixture.level.getServer().getCommands().performPrefixedCommand(source,"rg define -w world -g " + parent);
            check(fixture.regions.containsKey(parent) && fixture.regions.get(parent).type().equals("global"),"The original -w/-g syntax must create a named nonphysical template through CommandEvent");
            fixture.command(source,"rg pos1 " + coordinates(fixture.pos(3,1,3))); fixture.command(source,"rg pos2 " + coordinates(fixture.pos(5,5,5)));
            fixture.level.getServer().getCommands().performPrefixedCommand(source,"region define -w world " + child);
            check(fixture.regions.containsKey(child),"The original region alias and world option must create the actual selected claim");
            fixture.level.getServer().getCommands().performPrefixedCommand(source,"rg setparent -w world " + child + " " + parent);
            fixture.level.getServer().getCommands().performPrefixedCommand(source,"rg flag -w world " + parent + " greeting \"a literal -w and \\\"quote\\\"\"");
            check(parent.equals(fixture.regions.get(child).parent()) && "a literal -w and \"quote\"".equals(fixture.regions.get(parent).flags().get("greeting")),"Translation must preserve parent relations and quoted literal options");
            fixture.level.getServer().getCommands().performPrefixedCommand(source,"rg remove -w world -f " + parent);
            check(!fixture.regions.containsKey(parent) && !fixture.regions.containsKey(child),"The original recursive removal option must remove the complete inheritance tree");
        }
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void buildPermissionsAndPotionOverridesUseOriginalRules(GameTestHelper helper) throws Exception {
        try(Fixture fixture=new Fixture(helper,"permission_nodes"); ChestConfiguration config=new ChestConfiguration(fixture.service,fixture.dimension)) {
            ServerPlayer player=fixture.player(4,2,4); BlockPos pos=fixture.pos(4,2,4); fixture.level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            Region region=fixture.addRegion(fixture.pos(3,1,3),fixture.pos(5,5,5)); region.owners().uuids().add(player.getUUID()); fixture.save();
            config.set("build-permission-nodes.enable",true);
            TestPermissionProvider provider=new TestPermissionProvider(player.getUUID()); NeoForge.EVENT_BUS.register(provider);
            try {
                var denied=new BlockEvent.BreakEvent(fixture.level,pos,fixture.level.getBlockState(pos),player); NeoForge.EVENT_BUS.post(denied);
                check(denied.isCanceled(),"Region ownership must not override a separately enabled build permission rule");
                provider.allowed.add("worldguard.build.block.stone.remove");
                var permitted=new BlockEvent.BreakEvent(fixture.level,pos,fixture.level.getBlockState(pos),player); NeoForge.EVENT_BUS.post(permitted);
                check(!permitted.isCanceled(),"The original material-before-action removal permission must grant access");
                provider.allowed.clear(); provider.allowed.add("worldguard.build.block.remove.stone");
                var alternate=new BlockEvent.BreakEvent(fixture.level,pos,fixture.level.getBlockState(pos),player); NeoForge.EVENT_BUS.post(alternate);
                check(!alternate.isCanceled(),"The original action-before-material permission must also grant access");
                config.set("build-permission-nodes.enable",false); config.set("gameplay.block-potions",List.of("SPEED"));
                ItemStack potion=new ItemStack(net.minecraft.world.item.Items.SPLASH_POTION);
                potion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.SWIFTNESS));
                check(!com.sk89q.worldguard.neoforge.rules.WorldRulesHooks.potionUse(fixture.level,player,potion),"A configured blocked effect must prevent throwing its potion");
                provider.allowed.add("worldguard.override.potions");
                check(com.sk89q.worldguard.neoforge.rules.WorldRulesHooks.potionUse(fixture.level,player,potion),"The native override permission must allow blocked potions");
                config.set("gameplay.block-potions-overly-reliably",true);
                check(!com.sk89q.worldguard.neoforge.rules.WorldRulesHooks.potionUse(fixture.level,player,potion),"Reliable blocking must prohibit the thrown potion even with the override permission");
                config.set("ignition.block-lighter",true);
                check(!fixture.service.canUse(fixture.level,pos,player,"lighter"),"A global lighter prohibition must apply without its override permission");
                provider.allowed.add("worldguard.override.lighter");
                check(fixture.service.canUse(fixture.level,pos,player,"lighter"),"The original lighter override permission must permit ignition in an owned claim");
            } finally { NeoForge.EVENT_BUS.unregister(provider); }
        }
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void nativeFrostWalkerRespectsMembershipBeforeChangingWater(GameTestHelper helper) throws Exception {
        try(Fixture fixture=new Fixture(helper,"frost_membership")) {
            ServerPlayer player=fixture.player(4,2,4); BlockPos water=fixture.pos(4,2,4); fixture.level.setBlock(water,Blocks.WATER.defaultBlockState(),2);
            Region region=fixture.addRegion(fixture.pos(3,1,3),fixture.pos(5,5,5));
            var effect=new net.minecraft.world.item.enchantment.effects.ReplaceDisk(net.minecraft.world.item.enchantment.LevelBasedValue.constant(2),net.minecraft.world.item.enchantment.LevelBasedValue.constant(1),net.minecraft.core.Vec3i.ZERO,
                    java.util.Optional.of(net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate.matchesBlocks(Blocks.WATER)),net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(Blocks.FROSTED_ICE),java.util.Optional.empty());
            var item=new net.minecraft.world.item.enchantment.EnchantedItemInUse(new ItemStack(net.minecraft.world.item.Items.IRON_BOOTS),net.minecraft.world.entity.EquipmentSlot.FEET,player);
            effect.apply(fixture.level,1,item,player,Vec3.atCenterOf(water));
            check(fixture.level.getBlockState(water).is(Blocks.WATER),"Actual enchantment disk replacement must preserve protected water for a nonmember");
            region.members().uuids().add(player.getUUID()); fixture.save(); effect.apply(fixture.level,1,item,player,Vec3.atCenterOf(water));
            check(fixture.level.getBlockState(water).is(Blocks.FROSTED_ICE),"The same actual enchantment must freeze water after membership is granted");
        }
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void operatorsReceiveSessionEffectsAndInventoryStackPreservesItems(GameTestHelper helper) throws Exception {
        try(Fixture fixture=new Fixture(helper,"operator_sessions")) {
            ServerPlayer player=fixture.player(4,2,4); var ops=fixture.level.getServer().getPlayerList().getOps();
            ops.add(new net.minecraft.server.players.ServerOpListEntry(player.getGameProfile(),4,false));
            try {
                check(fixture.service.bypass(player),"The test must use a real vanilla operator with regional bypass enabled");
                player.setGameMode(GameType.SURVIVAL); player.setHealth(8); player.getFoodData().setFoodLevel(8);
                Region region=fixture.addRegion(fixture.pos(3,1,3),fixture.pos(5,5,5));
                region.flags().put("heal-amount",1); region.flags().put("heal-delay",0); region.flags().put("feed-amount",1); region.flags().put("feed-delay",0); region.flags().put("send-chat","deny"); fixture.save();
                fixture.service.sessions().tick(player);
                check(player.getHealth()==20 && player.getFoodData().getFoodLevel()==20,"OP bypass must preserve WorldGuard's instant healing and feeding session effects");
                var chat=new net.neoforged.neoforge.event.ServerChatEvent(player,"blocked",Component.literal("blocked")); NeoForge.EVENT_BUS.post(chat);
                check(chat.isCanceled(),"The send-chat session flag must also restrict an operator with build bypass");
                player.getInventory().clearContent(); player.getInventory().setItem(1,new ItemStack(net.minecraft.world.item.Items.DIRT,40)); player.getInventory().setItem(3,new ItemStack(net.minecraft.world.item.Items.DIRT,40));
                player.getInventory().setItem(4,new ItemStack(net.minecraft.world.item.Items.STONE,10)); player.getInventory().setItem(6,new ItemStack(net.minecraft.world.item.Items.STONE,10));
                var source=player.createCommandSourceStack().withSuppressedOutput(); fixture.command(source,"stack");
                check(player.getInventory().getItem(1).getCount()==64 && player.getInventory().getItem(3).getCount()==16 && player.getInventory().getItem(4).getCount()==20 && player.getInventory().getItem(6).isEmpty(),"The stack command must compact every material without creating or removing items");
                fixture.command(source,"locate @s"); check(player.blockPosition().equals(fixture.service.compassTarget(player)),"The locate command must set the vanilla personal compass target");
                fixture.command(source,"locate"); check(fixture.service.compassTarget(player)==null,"The empty locate command must reset the personal compass to world spawn");
            } finally { ops.remove(player.getGameProfile()); }
        }
        helper.succeed();
    }

    public static final class TestPermissionProvider {
        private final UUID player; private final Set<String> allowed=new java.util.HashSet<>();
        private TestPermissionProvider(UUID player) { this.player=player; }
        @net.neoforged.bus.api.SubscribeEvent public void permission(com.sk89q.worldguard.neoforge.api.RegionPermissionEvent event) {
            if(event.player().getUUID().equals(player)) event.setAllowed(allowed.contains(event.permission()));
        }
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void nativeSpawnEggDenialsPreserveInventoryAndModSpawnOptionIsRespected(GameTestHelper helper) throws Exception {
        try(Fixture fixture=new Fixture(helper,"egg_causes"); ChestConfiguration config=new ChestConfiguration(fixture.service,fixture.dimension)) {
            ServerPlayer player=fixture.player(4,2,4); BlockPos pos=fixture.pos(4,2,4); fixture.level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);
            Region region=fixture.addRegion(fixture.pos(3,1,3),fixture.pos(5,5,5)); region.members().uuids().add(player.getUUID()); region.flags().put("mob-spawning","deny"); fixture.save();
            ItemStack egg=new ItemStack(net.minecraft.world.item.Items.COW_SPAWN_EGG,2); player.setItemInHand(InteractionHand.MAIN_HAND,egg);
            var hit=new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false);
            var result=egg.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
            check(result==net.minecraft.world.InteractionResult.FAIL && egg.getCount()==2,"A denied native spawn egg must return failure without consuming inventory");
            check(fixture.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class,new net.minecraft.world.phys.AABB(pos).inflate(1)).isEmpty(),"A denied native spawn egg must create no cow");
            var blocked=EntityType.COW.create(fixture.level); blocked.setPos(Vec3.atBottomCenterOf(pos));
            check(!fixture.level.addFreshEntity(blocked),"Direct mod spawns are checked by default"); blocked.discard();
            config.set("mobs.block-plugin-spawning",false);
            var custom=EntityType.COW.create(fixture.level); custom.setPos(Vec3.atBottomCenterOf(pos));
            try { check(fixture.level.addFreshEntity(custom),"The native option must allow an untracked direct mod spawn"); } finally { custom.discard(); }
            fixture.regions.get(fixture.id).flags().put("mob-spawning","allow"); fixture.save();
            check(egg.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction() && egg.getCount()==1,"The same egg must consume exactly one item after spawning is allowed");
            var cows=fixture.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class,new net.minecraft.world.phys.AABB(pos).inflate(2));
            check(cows.size()==1,"A permitted native spawn egg must create one cow"); cows.forEach(Entity::discard);
        }
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void diagnosticsPagedFlagsAndProfilerRunAsNativeCommands(GameTestHelper helper) throws Exception {
        try(Fixture fixture=new Fixture(helper,"diagnostics")) {
            ServerPlayer player=fixture.player(4,2,1); var source=player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            fixture.level.setBlock(fixture.pos(4,3,4),Blocks.STONE.defaultBlockState(),2); player.setYRot(0); player.setXRot(0);
            fixture.addRegion(fixture.pos(3,1,3),fixture.pos(5,5,5));
            fixture.command(source,"wg debug testbreak @s -t -s");
            check(fixture.level.getBlockState(fixture.pos(4,3,4)).is(Blocks.STONE),"Event diagnostics must not actually break the traced block");
            fixture.level.getServer().getCommands().performPrefixedCommand(source,"rg flags -w world -p 2 " + fixture.id);
            fixture.command(source,"wg flushstates @s"); fixture.command(source,"wg running");
            fixture.command(source,"wg report");
            check(Files.readString(fixture.service.directory().resolve("report.txt")).contains("Configuration (credentials omitted)"),"The report command must write its native diagnostic file");
            fixture.command(source,"wg profile 1 -i 20 -t \"Server thread\""); check(fixture.service.profiler().running(),"Native profiler command must start the actual stack sampler");
            fixture.command(source,"wg stopprofile"); check(!fixture.service.profiler().running(),"Native stopprofile must stop the actual sampler");
        }
        helper.succeed();
    }

    public static final class TestGroupProvider {
        private final UUID player;
        public TestGroupProvider(UUID player) { this.player=player; }
        @net.neoforged.bus.api.SubscribeEvent public void group(com.sk89q.worldguard.neoforge.api.RegionGroupEvent event) {
            if (event.player().getUUID().equals(player) && event.group().equals("wgtest-builders")) event.setMember(true);
        }
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void embeddedMysqlDriverCanReadWriteAndLogOnTheNativeServer(GameTestHelper helper) throws Exception {
        String dsn=System.getenv("WORLDGUARD_TEST_MYSQL_DSN");
        if(dsn==null || dsn.isBlank()) { WorldGuardMod.LOGGER.info("Skipping optional native MySQL branch: WORLDGUARD_TEST_MYSQL_DSN is unset"); helper.succeed(); return; }
        String prefix="wgnative_" + UUID.randomUUID().toString().replace("-","").substring(0,12) + "_";
        var settings=new com.sk89q.worldguard.neoforge.storage.JdbcSettings(dsn,System.getenv().getOrDefault("WORLDGUARD_TEST_MYSQL_USER","root"),System.getenv().getOrDefault("WORLDGUARD_TEST_MYSQL_PASSWORD",""),prefix);
        try {
            var sql=new com.sk89q.worldguard.neoforge.storage.SqlRegionStorage(settings,Map.of());
            Region region=Region.cuboid("native",0,-64,0,3,320,3); region.flags().put("pvp","deny");
            sql.save("minecraft:overworld",Map.of("native",region));
            check("deny".equals(sql.load("minecraft:overworld").get("native").flags().get("pvp")),"The actual NeoForge classloader must read and write with its embedded MySQL driver");
            new com.sk89q.worldguard.neoforge.storage.SqlBlacklistLogger(settings,"blacklist").log("on-use","minecraft:overworld","test",0,0,0,"minecraft:tnt","native logging");
            try(var c=settings.open();var s=c.createStatement();var r=s.executeQuery("SELECT event FROM " + settings.table("blacklist"))) { check(r.next() && r.getString(1).equals("USE"),"Native MySQL blacklist logging must store the original event format"); }
        } finally {
            try(var c=settings.open();var s=c.createStatement()) { for(String suffix:List.of("region_poly2d_point","region_cuboid","region_poly2d","region_flag","region_players","region_groups","region","user","group","world","blacklist","migrations")) s.executeUpdate("DROP TABLE IF EXISTS " + settings.table(suffix)); }
        }
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void nativeFireAndTreeGrowthCannotCrossProtectedBoundaries(GameTestHelper helper) throws Exception {
        try(Fixture fixture=new Fixture(helper,"fire_tree_boundary")) {
            fixture.addRegion(fixture.pos(3,1,0),fixture.pos(7,15,7));
            BlockPos sapling=fixture.pos(2,2,4);
            for(int x=0;x<8;x++) for(int z=0;z<8;z++) fixture.level.setBlock(fixture.pos(x,1,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
            var before=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            for(int x=3;x<8;x++) for(int z=0;z<8;z++) for(int y=2;y<13;y++) { var destination=fixture.pos(x,y,z); before.put(destination,fixture.level.getBlockState(destination)); }
            var state=Blocks.OAK_SAPLING.defaultBlockState().setValue(net.minecraft.world.level.block.SaplingBlock.STAGE,1); fixture.level.setBlock(sapling,state,2);
            ((net.minecraft.world.level.block.SaplingBlock)Blocks.OAK_SAPLING).advanceTree(fixture.level,sapling,state,net.minecraft.util.RandomSource.create(4));
            check(fixture.level.getBlockState(sapling.above()).is(net.minecraft.tags.BlockTags.LOGS),"The real tree feature must grow its unprotected trunk");
            for(var entry : before.entrySet()) check(fixture.level.getBlockState(entry.getKey()).equals(entry.getValue()),"The real tree feature changed protected " + entry.getKey() + " from " + entry.getValue() + " to " + fixture.level.getBlockState(entry.getKey()));
            BlockPos log=fixture.pos(3,2,2),fire=log.west(); fixture.level.setBlock(log,Blocks.OAK_LOG.defaultBlockState(),2); fixture.level.setBlock(fire,Blocks.FIRE.defaultBlockState(),2);
            var burn=net.minecraft.world.level.block.FireBlock.class.getDeclaredMethod("checkBurnOut",net.minecraft.world.level.Level.class,BlockPos.class,int.class,net.minecraft.util.RandomSource.class,int.class,Direction.class); burn.setAccessible(true);
            burn.invoke(Blocks.FIRE,fixture.level,log,1,net.minecraft.util.RandomSource.create(0),1,Direction.WEST);
            check(fixture.level.getBlockState(log).is(Blocks.OAK_LOG),"A real fire burn must preserve a block across an unowned claim boundary");
            fixture.regions.get(fixture.id).flags().put("block-break","allow"); fixture.save(); fixture.level.setBlock(fire,Blocks.FIRE.defaultBlockState(),2);
            burn.invoke(Blocks.FIRE,fixture.level,log,1,net.minecraft.util.RandomSource.create(0),1,Direction.WEST);
            check(!fixture.level.getBlockState(log).is(Blocks.OAK_LOG),"Explicit block-break allow must permit the same actual burn");
        }
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void nativeFrequencyAndEntityOriginSettingsChangeAssociation(GameTestHelper helper) throws Exception {
        try(Fixture fixture=new Fixture(helper,"origin_frequency"); ChestConfiguration config=new ChestConfiguration(fixture.service,fixture.dimension)) {
            BlockPos outside=fixture.pos(1,2,1),inside=fixture.pos(4,2,4); Region region=fixture.addRegion(fixture.pos(3,1,3),fixture.pos(5,5,5));
            region.flags().put("fire-spread","deny"); fixture.save();
            check(!fixture.service.allows(fixture.level,inside,null,"fire-spread"),"Native high frequency checks must enforce a configured fire flag by default");
            config.set("regions.high-frequency-flags",false);
            check(fixture.service.allows(fixture.level,inside,null,"fire-spread"),"An explicit false must disable the original high frequency fire flag check");
            config.set("fire.disable-all-fire-spread",true);
            check(!fixture.service.allows(fixture.level,inside,null,"fire-spread"),"Disabling a regional high frequency flag must preserve the global fire ban");
            var cow=EntityType.COW.create(fixture.level); cow.setPos(Vec3.atBottomCenterOf(outside)); fixture.level.addFreshEntity(cow); cow.setPos(Vec3.atBottomCenterOf(inside));
            try {
                check(ProtectionHooks.mobChange(fixture.level,inside,cow),"Default mob association must use its current region");
                config.set("regions.use-paper-entity-origin",true);
                check(!ProtectionHooks.mobChange(fixture.level,inside,cow),"Enabling origin association must preserve the mob's unowned initial spawn region");
                var tag=new net.minecraft.nbt.CompoundTag(); check(cow.save(tag),"The test mob must save to its real native entity NBT");
                var loaded=EntityType.loadEntityRecursive(tag,fixture.level,entity->entity);
                check(loaded!=null && fixture.service.origin(loaded).equals(outside),"Native entity serialization must retain WorldGuard's spawn origin");
            } finally { cow.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void woodenAxeClaimsAndOwnerCommandsWorkWithoutOp(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "axe_claim")) {
            ServerPlayer player = fixture.player(2, 2, 2);
            CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput();
            check(!source.hasPermission(2), "The selection and claim test must use a real nonoperator source");
            BlockPos first = fixture.pos(3, 1, 3), second = fixture.pos(5, 4, 5);
            fixture.level.setBlock(first, Blocks.STONE.defaultBlockState(), 2);
            fixture.level.setBlock(second, Blocks.OAK_LOG.defaultBlockState(), 2);
            ItemStack axe = new ItemStack(net.minecraft.world.item.Items.WOODEN_AXE);
            player.setItemInHand(InteractionHand.MAIN_HAND, axe);
            player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            player.gameMode.handleBlockBreakAction(first, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    Direction.UP, fixture.level.getMaxBuildHeight(), 0);
            check(fixture.level.getBlockState(first).is(Blocks.STONE), "Selecting pos1 must not break a block even in creative mode");
            player.gameMode.handleBlockBreakAction(second, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    Direction.UP, fixture.level.getMaxBuildHeight(), 1);
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            var result = player.gameMode.useItemOn(player, fixture.level, axe, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(second), Direction.UP, second, false));
            check(result.consumesAction(), "Selecting pos2 must consume the block interaction");
            check(fixture.level.getBlockState(second).is(Blocks.OAK_LOG) && axe.getDamageValue() == 0,
                    "Selecting pos2 must neither strip a log nor damage the wooden axe");
            fixture.ids.add(fixture.id);
            fixture.command(source, "rg");
            fixture.command(source, "rg define " + fixture.id);
            Region region = fixture.regions.get(fixture.id);
            check(region != null && region.minX() == first.getX() && region.minY() == first.getY()
                            && region.minZ() == first.getZ() && region.maxX() == second.getX()
                            && region.maxY() == second.getY() && region.maxZ() == second.getZ(),
                    "A nonoperator's wooden-axe selection must become the exact saved cuboid");
            check(region.owners().uuids().contains(player.getUUID()), "Nonoperator define must automatically make the claimant an owner");
            fixture.command(source, "regions flag " + fixture.id + " pvp deny");
            fixture.command(source, "region addmember " + fixture.id + " Friend -n");
            fixture.command(source, "rg addowner " + fixture.id + " CoOwner -n");
            fixture.command(source, "rg removemember " + fixture.id + " Friend -n");
            fixture.command(source, "rg removeowner " + fixture.id + " CoOwner -n");
            check("deny".equals(region.flags().get("pvp")) && region.members().isEmpty()
                            && region.owners().uuids().contains(player.getUUID()) && region.owners().players().isEmpty(),
                    "Only owner commands must change and save flag/member/owner domains without OP");
            check(fixture.service.canBuild(fixture.level, first, player), "A normal owner must be able to build in their claim");
            fixture.command(source, "rg remove " + fixture.id);
            check(!fixture.regions.containsKey(fixture.id), "A nonoperator must be able to remove their own claim");
            fixture.command(source, "region claim " + fixture.id);
            check(fixture.regions.get(fixture.id).owners().uuids().contains(player.getUUID()), "Claim must also work without OP");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void playerClaimsCannotBypassLimitsOrForeignOwnership(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "claim_permissions");
             ChestConfiguration config = new ChestConfiguration(fixture.service, fixture.dimension)) {
            ServerPlayer player = fixture.player(2, 2, 2);
            CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput();
            check(!source.hasPermission(2), "Ownership checks must run with no operator permissions");
            BlockPos first = fixture.pos(2, 1, 2), second = fixture.pos(3, 3, 3);
            fixture.command(source, "rg pos1 " + coordinates(first));
            fixture.command(source, "rg pos2 " + coordinates(second));
            config.set("regions.max-claim-volume", 1);
            fixture.rejectedCommand(source, "rg define " + fixture.id);
            fixture.rejectedCommand(source, "region claim " + fixture.id);
            check(!fixture.regions.containsKey(fixture.id), "Neither define nor claim may bypass a nonoperator's volume limit");
            config.set("regions.max-claim-volume", 1000);
            config.set("regions.max-region-count-per-player.default", 1);
            fixture.ids.add(fixture.id);
            fixture.command(source, "rg define " + fixture.id);
            config.set("regions.claim-only-inside-existing-regions", true);
            String extra = fixture.id + "_extra", foreignId = fixture.id + "_foreign";
            fixture.ids.add(extra); fixture.ids.add(foreignId);
            fixture.rejectedCommand(source, "rg create " + extra + " OtherOwner -n");
            check(!fixture.regions.containsKey(extra), "Extra owners must not circumvent the claimant's count limit");
            fixture.command(source, "rg redefine " + fixture.id);
            Region owned = fixture.regions.get(fixture.id);
            Region foreign = Region.cuboid(foreignId, fixture.pos(5, 1, 5).getX(), first.getY(), fixture.pos(5, 1, 5).getZ(),
                    fixture.pos(7, 4, 7).getX(), second.getY() + 1, fixture.pos(7, 4, 7).getZ());
            foreign.owners().uuids().add(UUID.randomUUID());
            foreign.members().uuids().add(player.getUUID());
            fixture.regions.put(foreignId, foreign); fixture.save();
            for (String command : List.of("rg flag " + foreignId + " pvp allow", "rg remove " + foreignId,
                    "rg delete " + foreignId + " children", "rg redefine " + foreignId,
                    "rg addowner " + foreignId + " " + player.getUUID(), "rg removemember " + foreignId + " -a"))
                fixture.rejectedCommand(source, command);
            check(foreign.flags().isEmpty() && !foreign.owners().uuids().contains(player.getUUID())
                            && foreign.members().uuids().contains(player.getUUID()) && fixture.regions.get(foreignId) == foreign,
                    "Membership must not grant ownership or allow changes to another player's claim");
            fixture.command(source, "rg pos2 " + coordinates(fixture.pos(7, 4, 7)));
            fixture.rejectedCommand(source, "rg redefine " + fixture.id);
            check(fixture.regions.get(fixture.id) == owned && owned.maxX() == second.getX(),
                    "Redefining an owned region must not expand it into someone else's claim");
            fixture.command(source, "rg pos2 " + coordinates(second));
            config.set("regions.max-claim-volume", 1);
            Region beforeResize = fixture.regions.get(fixture.id);
            fixture.rejectedCommand(source, "rg redefine " + fixture.id);
            check(fixture.regions.get(fixture.id) == beforeResize, "Redefinition must enforce volume limits without changing the saved region");
            for (String command : List.of("rg bypass on", "region toggle-bypass on", "regions global",
                    "rg define-global " + extra, "rg redefine-global " + fixture.id, "rg priority " + fixture.id + " 5",
                    "rg parent " + fixture.id + " " + foreignId, "rg in minecraft:overworld save", "rg reload",
                    "rg import missing.yml", "rg migrateuuid", "rg migratedb yaml sql", "rg migrateheights", "wg reload", "god"))
                fixture.rejectedCommand(source, command);
            fixture.rejectedCommand(source, "rg flag __global__ pvp allow");
            fixture.level.getServer().getCommands().performPrefixedCommand(source, "rg define -g " + extra);
            check(!fixture.regions.containsKey(extra) && !fixture.regions.containsKey("__global__")
                            && !fixture.service.bypass(player), "Administrative aliases and legacy options must not grant nonoperators a bypass or global region");
            String nether = "minecraft:the_nether";
            try (ChestConfiguration otherWorld = new ChestConfiguration(fixture.service, nether)) {
                otherWorld.set("regions.max-region-count-per-player.default", 0);
                fixture.command(source, "rg in " + nether + " pos1 0 0 0");
                fixture.command(source, "rg in " + nether + " pos2 1 1 1");
                fixture.rejectedCommand(source, "rg in " + nether + " define " + extra);
                check(!fixture.service.repository().regions(nether).containsKey(extra),
                        "A cross-dimension claim must enforce the selected dimension's count limit");
            } finally {
                fixture.service.repository().regions(nether).remove(extra);
                fixture.service.repository().save(nether);
            }
        }
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nonoperatorCommandsSurviveVanillaPacketSerialization(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper, "command_packet")) {
            ServerPlayer player = fixture.player(2, 2, 2);
            CommandSourceStack source = player.createCommandSourceStack();
            var serverRoot = fixture.level.getServer().getCommands().getDispatcher().getRoot();
            var clientRoot = new com.mojang.brigadier.tree.RootCommandNode<net.minecraft.commands.SharedSuggestionProvider>();
            var nodes = new java.util.HashMap<com.mojang.brigadier.tree.CommandNode<CommandSourceStack>,
                    com.mojang.brigadier.tree.CommandNode<net.minecraft.commands.SharedSuggestionProvider>>();
            net.neoforged.neoforge.server.command.CommandHelper.mergeCommandNode(serverRoot, clientRoot, nodes, source,
                    context -> 0, suggestion -> net.minecraft.commands.synchronization.SuggestionProviders.safelySwap(
                            (com.mojang.brigadier.suggestion.SuggestionProvider<net.minecraft.commands.SharedSuggestionProvider>)
                                    (com.mojang.brigadier.suggestion.SuggestionProvider<?>) suggestion));
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                net.minecraft.network.protocol.game.ClientboundCommandsPacket.STREAM_CODEC.encode(buffer,
                        new net.minecraft.network.protocol.game.ClientboundCommandsPacket(clientRoot));
                var packet = net.minecraft.network.protocol.game.ClientboundCommandsPacket.STREAM_CODEC.decode(buffer);
                var received = packet.getRoot(net.minecraft.commands.CommandBuildContext.simple(
                        fixture.level.getServer().registryAccess(), fixture.level.enabledFeatures()));
                for (String alias : List.of("rg", "region", "regions")) {
                    var node = received.getChild(alias);
                    check(node != null && node.getCommand() != null, "Vanilla command packets must expose " + alias + " to a nonoperator");
                    var target = node.getRedirect() == null ? node : node.getRedirect();
                    check(target.getChild("claim") != null && target.getChild("define") != null
                                    && target.getChild("flag") != null && target.getChild("pos1") != null,
                            "The client must receive the complete ordinary region command tree");
                    for (String admin : List.of("bypass", "global", "define-global", "redefine-global", "priority", "parent",
                            "save", "reload", "import", "migrateuuid", "migratedb", "migrateheights"))
                        check(target.getChild(admin) == null, "The nonoperator's command tree must hide " + admin);
                    var dimension = target.getChild("in").getChild("dimension");
                    check(dimension.getChild("bypass") == null && dimension.getChild("save") == null,
                            "Selecting a dimension must not expose administrator commands");
                }
                check(received.getChild("wg") == null && received.getChild("worldguard") == null
                                && received.getChild("god") == null, "General administrator commands must remain hidden from ordinary players");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    /** Restores both the exact config and its preexisting backup after every outcome. */
    private static final class ChestConfiguration implements AutoCloseable {
        private final ProtectionService service;
        private final String dimension;
        private final Path config;
        private final Path backup;
        private final Path imported;
        private final byte[] original;
        private final byte[] originalBackup;
        private final Map<String, Object> values;

        private ChestConfiguration(ProtectionService service, String dimension) throws IOException {
            this.service = service;
            this.dimension = dimension;
            config = dimension == null ? service.directory().resolve("config.yml")
                    : WorldGuardConfig.worldDirectory(service.directory().resolve("worlds"), dimension).resolve("config.yml");
            backup = config.resolveSibling("config.yml.bak");
            original = Files.exists(config) ? Files.readAllBytes(config) : null;
            originalBackup = Files.exists(backup) ? Files.readAllBytes(backup) : null;
            values = WorldGuardConfig.read(config);
            Files.createDirectories(service.directory().resolve("import"));
            imported = Files.createTempFile(service.directory().resolve("import"), "gametest-chest-", ".yml");
            try {
                enabled(true);
            } catch (IOException | RuntimeException exception) {
                try { close(); } catch (IOException restoration) { exception.addSuppressed(restoration); }
                throw exception;
            }
        }

        private void enabled(boolean enabled) throws IOException {
            set("chest-protection.enable", enabled);
        }

        private void set(String path, Object value) throws IOException {
            String[] parts = path.split("\\.");
            Map<String, Object> current = values;
            for (int index = 0; index < parts.length - 1; index++) {
                Map<String, Object> child = new LinkedHashMap<>();
                if (current.get(parts[index]) instanceof Map<?, ?> existing) existing.forEach((key, item) -> child.put(String.valueOf(key), item));
                current.put(parts[index], child); current = child;
            }
            current.put(parts[parts.length - 1], value);
            Files.writeString(imported, new Yaml().dump(values));
            service.importConfiguration(imported, dimension);
        }

        @Override
        public void close() throws IOException {
            try {
                if (original == null) Files.deleteIfExists(config); else Files.write(config, original);
                if (originalBackup == null) Files.deleteIfExists(backup); else Files.write(backup, originalBackup);
                service.reload();
            } finally {
                Files.deleteIfExists(imported);
            }
        }
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + " " + position.getY() + " " + position.getZ();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ProtectionService service;
        private final ServerLevel level;
        private final String dimension;
        private final String id;
        private final Map<String, Region> regions;
        private final Set<String> ids = new LinkedHashSet<>();
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<Path> files = new ArrayList<>();

        private Fixture(GameTestHelper helper, String suffix) throws IOException {
            this.helper = helper;
            this.service = WorldGuardMod.service();
            check(service != null, "WorldGuard must be initialized before GameTests");
            this.level = helper.getLevel();
            this.dimension = level.dimension().location().toString();
            this.id = "_wgtest_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
            this.regions = service.repository().regions(dimension);
        }

        private BlockPos pos(int x, int y, int z) {
            return helper.absolutePos(new BlockPos(x, y, z));
        }

        private Region addRegion(BlockPos min, BlockPos max) throws IOException {
            Region region = Region.cuboid(id, min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
            region.priority(Integer.MAX_VALUE);
            regions.put(id, region);
            ids.add(id);
            save();
            return region;
        }

        @SuppressWarnings("removal")
        private ServerPlayer player(int x, int y, int z) {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            players.add(player);
            BlockPos position = pos(x, y, z);
            player.setPos(position.getX() + .5, position.getY(), position.getZ() + .5);
            if (service.bypass(player)) {
                service.toggleBypass(player);
            }
            return player;
        }

        private void save() throws IOException {
            service.repository().save(dimension);
        }

        private void command(CommandSourceStack source, String command) throws Exception {
            int result = level.getServer().getCommands().getDispatcher().execute(command, source);
            check(result > 0, "Command must succeed: " + command);
        }
        private void rejectedCommand(CommandSourceStack source, String command) throws Exception {
            try {
                int result = level.getServer().getCommands().getDispatcher().execute(command, source);
                check(result == 0, "Command must be rejected: " + command);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
                // Brigadier reports validation failures as command exceptions.
            }
        }

        @Override
        public void close() throws IOException {
            ids.forEach(regions::remove);
            IOException failure = null;
            try {
                save();
            } catch (IOException exception) {
                failure = exception;
            }
            for (ServerPlayer player : players) {
                RegionCommands.logout(player.getUUID());
                service.logout(player);
                level.getServer().getPlayerList().remove(player);
                player.discard();
            }
            for (Path file : files) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
