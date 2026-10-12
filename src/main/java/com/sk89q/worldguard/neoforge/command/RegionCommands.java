package com.sk89q.worldguard.neoforge.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.google.gson.JsonParser;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import com.sk89q.worldguard.neoforge.region.Domain;
import com.sk89q.worldguard.neoforge.region.FlagRegistry;
import com.sk89q.worldguard.neoforge.region.Region;
import com.sk89q.worldguard.neoforge.region.RegionQuery;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** Players manage their own claims; server administration uses vanilla operator levels. */
public final class RegionCommands {
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final SuggestionProvider<CommandSourceStack> REGIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(regions(context).keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> FLAGS = (context, builder) ->
            SharedSuggestionProvider.suggest(FlagRegistry.names(), builder);
    private static final SuggestionProvider<CommandSourceStack> PLAYERS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerNames(), builder);

    private RegionCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> region = Commands.literal("region")
                .executes(guarded(RegionCommands::help));
        children(region);
        RequiredArgumentBuilder<CommandSourceStack, ?> dimension = Commands.argument("dimension", DimensionArgument.dimension());
        children(dimension);
        region.then(Commands.literal("in").then(dimension));
        var regionNode = dispatcher.register(region);
        dispatcher.register(Commands.literal("rg")
                .executes(regionNode.getCommand()).redirect(regionNode));
        dispatcher.register(Commands.literal("regions")
                .executes(regionNode.getCommand()).redirect(regionNode));

        LiteralArgumentBuilder<CommandSourceStack> worldguard = Commands.literal("worldguard")
                .requires(RegionCommands::operator).executes(guarded(RegionCommands::adminHelp));
        worldguard.then(Commands.literal("version").executes(guarded(context -> {
            String version = ModList.get().getModContainerById("worldguard")
                    .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
            return feedback(context, "WorldGuard NeoForge " + version + " (Minecraft 1.21.1). WorldEdit: "
                    + (WorldEditSelectionBridge.available() ? "available" : "absent; native selection enabled"));
        })));
        worldguard.then(Commands.literal("reload").executes(guarded(context -> {
            WorldGuardMod.service().reload();
            return feedback(context, "WorldGuard configuration and regions reloaded.");
        })));
        worldguard.then(Commands.literal("report").executes(guarded(RegionCommands::report)));
        var debug=Commands.literal("debug").executes(guarded(RegionCommands::report));
        for(String name : List.of("testbreak","testplace","testinteract","testdamage")) debug.then(Commands.literal(name)
                .executes(guarded(c -> com.sk89q.worldguard.neoforge.admin.DebugCommands.run(c,name,"")))
                .then(Commands.argument("options",StringArgumentType.greedyString()).executes(guarded(c -> com.sk89q.worldguard.neoforge.admin.DebugCommands.run(c,name,StringArgumentType.getString(c,"options"))))));
        worldguard.then(debug);
        for(String name : List.of("running","queue")) worldguard.then(Commands.literal(name).executes(guarded(c -> feedback(c,"UUID lookups pending: " + WorldGuardMod.service().uuidLookups() + "; profiler active: " + WorldGuardMod.service().profiler().running() + ". Region writes run on the server thread."))));
        for (String name : List.of("flushstates", "clearstates")) {
            worldguard.then(Commands.literal(name).executes(guarded(context -> {
                WorldGuardMod.service().flushSessions();
                return feedback(context, "WorldGuard player sessions reset.");
            })).then(Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player()).executes(guarded(c -> {
                ServerPlayer player=net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player"); WorldGuardMod.service().sessions().logout(player);
                return feedback(c,"WorldGuard sessions reset for " + player.getGameProfile().getName() + ".");
            }))));
        }
        worldguard.then(Commands.literal("importconfig").then(Commands.argument("file", StringArgumentType.string())
                .suggests(RegionCommands::importSuggestions).executes(guarded(context -> importConfiguration(context, false)))
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(guarded(context -> importConfiguration(context, true))))));
        var worldguardNode = dispatcher.register(worldguard);
        dispatcher.register(Commands.literal("wg").requires(RegionCommands::operator)
                .executes(worldguardNode.getCommand()).redirect(worldguardNode));
    }

    private static boolean operator(CommandSourceStack source) {
        var service = WorldGuardMod.service();
        return source.hasPermission(service == null ? 2 : service.config().operatorLevel());
    }

    private static void children(ArgumentBuilder<CommandSourceStack, ?> root) {
        root.executes(guarded(RegionCommands::help));
        root.then(Commands.literal("help").executes(guarded(RegionCommands::help)));
        for (String name : List.of("pos1", "pos2")) {
            int position = name.equals("pos1") ? 1 : 2;
            root.then(Commands.literal(name).executes(guarded(context -> position(context, position, null)))
                    .then(Commands.argument("position", BlockPosArgument.blockPos())
                            .executes(guarded(context -> position(context, position, BlockPosArgument.getBlockPos(context, "position"))))));
        }
        root.then(Commands.literal("point").executes(guarded(context -> point(context, null)))
                .then(Commands.argument("position", BlockPosArgument.blockPos())
                        .executes(guarded(context -> point(context, BlockPosArgument.getBlockPos(context, "position"))))));
        root.then(Commands.literal("heights").then(Commands.argument("minY", IntegerArgumentType.integer())
                .then(Commands.argument("maxY", IntegerArgumentType.integer())
                        .executes(guarded(RegionCommands::heights)))));
        root.then(Commands.literal("selection").executes(guarded(RegionCommands::selectionInfo))
                .then(Commands.literal("clear").executes(guarded(context -> {
                    SELECTIONS.remove(context.getSource().getPlayerOrException().getUUID());
                    return feedback(context, "Native selection cleared. WorldEdit selection can now be used.");
                }))));
        for (String name : List.of("define", "def", "d", "create", "claim")) {
            boolean claim = name.equals("claim");
            root.then(Commands.literal(name).then(id().executes(guarded(context -> define(context, claim, null)))
                    .then(Commands.argument("owners", StringArgumentType.greedyString()).suggests(PLAYERS)
                            .executes(guarded(context -> define(context, claim, StringArgumentType.getString(context, "owners")))))));
        }
        root.then(Commands.literal("global").requires(RegionCommands::operator).executes(guarded(context -> defineGlobal(context))));
        root.then(Commands.literal("define-global").requires(RegionCommands::operator).then(id().executes(guarded(c->define(c,false,null,true)))
                .then(Commands.argument("owners",StringArgumentType.greedyString()).executes(guarded(c->define(c,false,StringArgumentType.getString(c,"owners"),true))))));
        root.then(Commands.literal("redefine-global").requires(RegionCommands::operator).then(id().executes(guarded(RegionCommands::redefineGlobal))));
        for (String name : List.of("redefine", "update", "move")) {
            root.then(Commands.literal(name).then(id().executes(guarded(RegionCommands::redefine))));
        }
        for (String name : List.of("remove", "delete", "del", "rem")) {
            root.then(Commands.literal(name).then(id().executes(guarded(context -> remove(context, false)))
                    .then(Commands.literal("force").executes(guarded(context -> remove(context, true))))));
            root.then(Commands.literal(name).then(id().then(Commands.literal("children").executes(guarded(RegionCommands::removeTree)))));
        }
        for (String name : List.of("info", "i")) {
            root.then(Commands.literal(name).executes(guarded(context -> infoHere(context, false)))
                    .then(id().executes(guarded(context -> info(context, existing(context))))));
        }
        root.then(Commands.literal("list").executes(guarded(context -> list(context, 1, null)))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(guarded(context -> list(context, IntegerArgumentType.getInteger(context, "page"), null))))
                .then(Commands.literal("owner").then(Commands.argument("owner", StringArgumentType.word()).suggests(PLAYERS)
                        .executes(guarded(context -> list(context, 1, StringArgumentType.getString(context, "owner"))))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(guarded(context ->
                                list(context, IntegerArgumentType.getInteger(context, "page"), StringArgumentType.getString(context, "owner"))))))));
        root.then(Commands.literal("list-filtered").then(Commands.argument("options",StringArgumentType.greedyString()).executes(guarded(RegionCommands::filteredList))));
        for (String name : List.of("select", "sel", "s")) {
            root.then(Commands.literal(name).executes(guarded(context -> infoHere(context, true)))
                    .then(id().executes(guarded(context -> select(context, existing(context))))));
        }
        for (String name : List.of("flag", "f")) {
            root.then(Commands.literal(name).then(id().then(Commands.argument("flag", StringArgumentType.word()).suggests(FLAGS)
                    .executes(guarded(context -> flag(context, null)))
                    .then(Commands.argument("value", StringArgumentType.greedyString()).suggests((context, builder) ->
                            SharedSuggestionProvider.suggest(List.of("allow", "deny", "true", "false", "here", "-g", "-e"), builder))
                            .executes(guarded(context -> flag(context, StringArgumentType.getString(context, "value"))))))));
        }
        root.then(Commands.literal("flags").executes(guarded(c -> flagList(c,null,1)))
                .then(id().executes(guarded(c -> flagList(c,StringArgumentType.getString(c,"id"),1)))));
        root.then(Commands.literal("flags-page").then(Commands.argument("options",StringArgumentType.greedyString()).executes(guarded(c -> {
            var words=CommandWords.parse(StringArgumentType.getString(c,"options")); String regionId=null; int page=1;
            for(int index=0;index<words.size();index++) { String word=words.get(index).text(); if(word.equals("-p")) { if(++index>=words.size()) throw error("Specify a page after -p"); page=Integer.parseInt(words.get(index).text()); } else if(regionId==null) regionId=word; else throw error("Specify one region ID"); }
            return flagList(c,regionId,page);
        }))));
        for (String name : List.of("setpriority", "priority", "pri")) {
            root.then(Commands.literal(name).requires(RegionCommands::operator).then(id().then(Commands.argument("priority", IntegerArgumentType.integer())
                    .executes(guarded(RegionCommands::priority)))));
        }
        for (String name : List.of("setparent", "parent", "par")) {
            root.then(Commands.literal(name).requires(RegionCommands::operator).then(id().executes(guarded(context -> parent(context, null)))
                    .then(Commands.argument("parent", StringArgumentType.string()).suggests(REGIONS)
                            .executes(guarded(context -> parent(context, StringArgumentType.getString(context, "parent")))))));
        }
        domainCommand(root, List.of("addmember", "addmem", "am"), false, false);
        domainCommand(root, List.of("addowner", "ao"), true, false);
        domainCommand(root, List.of("removemember", "remmember", "removemem", "remmem", "rm"), false, true);
        domainCommand(root, List.of("removeowner", "remowner", "ro"), true, true);
        for (String name : List.of("save", "write")) {
            root.then(Commands.literal(name).requires(RegionCommands::operator).executes(guarded(RegionCommands::save)));
        }
        for (String name : List.of("load", "reload")) {
            root.then(Commands.literal(name).requires(RegionCommands::operator).executes(guarded(RegionCommands::reload)));
        }
        for (String name : List.of("teleport", "tp")) {
            root.then(Commands.literal(name).then(id().executes(guarded(context -> teleport(context, "teleport")))
                    .then(Commands.literal("center").executes(guarded(context -> teleport(context, "center"))))
                    .then(Commands.literal("spawn").executes(guarded(context -> teleport(context, "spawn"))))));
        }
        for (String name : List.of("toggle-bypass", "bypass")) {
            root.then(Commands.literal(name).requires(RegionCommands::operator).executes(guarded(context -> bypass(context, null)))
                    .then(Commands.literal("on").executes(guarded(context -> bypass(context, true))))
                    .then(Commands.literal("off").executes(guarded(context -> bypass(context, false)))));
        }
        root.then(Commands.literal("import").requires(RegionCommands::operator).then(Commands.argument("file", StringArgumentType.string()).suggests(RegionCommands::importSuggestions)
                .executes(guarded(context -> importYaml(context, false)))
                .then(Commands.literal("replace").executes(guarded(context -> importYaml(context, true))))));
        root.then(Commands.literal("migrateuuid").requires(RegionCommands::operator).executes(guarded(RegionCommands::migrateUuid)));
        root.then(Commands.literal("migratedb").requires(RegionCommands::operator).then(Commands.argument("from", StringArgumentType.word()).suggests((c,b) -> SharedSuggestionProvider.suggest(List.of("yaml","sql"),b))
                .then(Commands.argument("to", StringArgumentType.word()).suggests((c,b) -> SharedSuggestionProvider.suggest(List.of("yaml","sql"),b))
                        .executes(guarded(c -> feedback(c, "Migrated " + WorldGuardMod.service().migrateDatabase(StringArgumentType.getString(c,"from"),StringArgumentType.getString(c,"to"))
                                + " regions. Target backups are in config/worldguard/backups. Set regions.sql.use for the destination and /wg reload."))))));
        root.then(Commands.literal("migrateheights").requires(RegionCommands::operator).executes(guarded(RegionCommands::migrateHeights)));
    }

    private static void domainCommand(ArgumentBuilder<CommandSourceStack, ?> root, List<String> names, boolean owners, boolean remove) {
        for (String name : names) {
            root.then(Commands.literal(name).then(id()
                    .then(Commands.argument("players", StringArgumentType.greedyString()).suggests(PLAYERS)
                            .executes(guarded(context -> domain(context, owners, remove, StringArgumentType.getString(context, "players")))))));
        }
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> id() {
        return Commands.argument("id", StringArgumentType.string()).suggests(REGIONS);
    }

    private static Command<CommandSourceStack> guarded(Action action) {
        return context -> {
            try {
                return action.run(context);
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                throw error(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            }
        };
    }

    @FunctionalInterface
    private interface Action {
        int run(CommandContext<CommandSourceStack> context) throws IOException, CommandSyntaxException;
    }

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }

    private static int feedback(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static ServerLevel level(CommandContext<CommandSourceStack> context) {
        try {
            return DimensionArgument.getDimension(context, "dimension");
        } catch (IllegalArgumentException exception) {
            return context.getSource().getLevel();
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private static String dimension(CommandContext<CommandSourceStack> context) {
        return level(context).dimension().location().toString();
    }

    private static Map<String, Region> regions(CommandContext<CommandSourceStack> context) {
        try {
            return WorldGuardMod.service().repository().regions(dimension(context));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load regions: " + exception.getMessage(), exception);
        }
    }

    private static Region existing(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "id").toLowerCase(Locale.ROOT);
        Region region = regions(context).get(id);
        if (region == null) {
            throw error("Region '" + id + "' does not exist in " + dimension(context) + ".");
        }
        return region;
    }

    private static RegionQuery ownership(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        return new RegionQuery(regions(context).values(), player.getUUID(), player.getGameProfile().getName())
                .withGroups(group -> WorldGuardMod.service().inGroup(player, group));
    }

    private static void requireOwner(CommandContext<CommandSourceStack> context, Region region) throws CommandSyntaxException {
        if (operator(context.getSource())) return;
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (region.type().equals("global") || !ownership(context, player).isOwner(region)) {
            throw error("You must own region '" + region.id() + "' to change it.");
        }
    }

    private static void persist(CommandContext<CommandSourceStack> context, Runnable change, Runnable rollback) throws IOException {
        change.run();
        try {
            WorldGuardMod.service().repository().save(dimension(context));
        } catch (IOException | RuntimeException exception) {
            rollback.run();
            throw exception;
        }
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        if (!operator(context.getSource())) return feedback(context,
                "WorldGuard: select two corners with a wooden axe (left click: pos1; right click: pos2), "
                + "then /rg claim <id> or /rg define <id>. Manage your regions with /rg info, list, select, "
                + "redefine, remove, flag, flags, addmember, removemember, addowner, removeowner, tp. "
                + "Selections also support /rg pos1, pos2, point and heights. Claim size/count limits and overlap protection apply.");
        return feedback(context, "WorldGuard: /rg pos1, pos2 [x y z]; point [x y z]; heights <minY> <maxY>; "
                + "define <id> [owners]; claim <id>; redefine <id>; remove <id> [force]; "
                + "info [id]; list [page]; select [id]; flag <id> <flag> [-g group] [value]; flags [id]; "
                + "addowner/addmember/removeowner/removemember <id> <names/UUIDs/g:groups>; "
                + "priority <id> <number>; parent <id> [parent]; tp <id> [center|spawn]; bypass [on|off]; "
                + "save; reload; import <file> [replace]. For another dimension: /rg in <dimension> <command>. "
                + "All changes save immediately. Remove a flag by omitting its value; use -e for an empty value; clear a domain with -a. "
                + "Use /rg selection clear to switch back to a WorldEdit selection.");
    }

    private static int adminHelp(CommandContext<CommandSourceStack> context) {
        return feedback(context, "WorldGuard administration: /wg version; /wg reload; /wg report; /wg running; /wg flushstates; "
                + "/wg importconfig <file> [dimension]. "
                + "Use /rg help for regions; Minecraft /debug and /jfr provide native server profiling.");
    }

    private static Selection nativeSelection(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID player = context.getSource().getPlayerOrException().getUUID();
        String dimension = dimension(context);
        return nativeSelection(player, dimension);
    }

    private static Selection nativeSelection(UUID player, String dimension) {
        Selection selection = SELECTIONS.get(player);
        if (selection == null || !selection.dimension.equals(dimension)) {
            selection = new Selection(dimension);
            SELECTIONS.put(player, selection);
        }
        return selection;
    }

    /** Returns false when the installed WorldEdit handles this player's selection wand. */
    static boolean wandPosition(ServerPlayer player, int number, BlockPos position) {
        if (WorldEditSelectionBridge.handlesSelectionWand(player)) {
            SELECTIONS.remove(player.getUUID());
            return false;
        }
        String dimension = player.serverLevel().dimension().location().toString();
        updatePosition(nativeSelection(player.getUUID(), dimension), number, position);
        player.sendSystemMessage(Component.literal("Точка " + number + ": " + position.toShortString() + " (" + dimension + ")."));
        return true;
    }

    private static void updatePosition(Selection selection, int number, BlockPos position) {
        selection.points.clear();
        if (number == 1) selection.first = position.immutable();
        else selection.second = position.immutable();
    }

    private static int position(CommandContext<CommandSourceStack> context, int number, BlockPos position) throws CommandSyntaxException {
        if (position == null) {
            if (context.getSource().getLevel() != level(context)) {
                throw error("Specify coordinates when selecting in another dimension.");
            }
            position = context.getSource().getPlayerOrException().blockPosition();
        }
        updatePosition(nativeSelection(context), number, position);
        return feedback(context, "Native position " + number + ": " + position.toShortString() + " (" + dimension(context) + ").");
    }

    private static int point(CommandContext<CommandSourceStack> context, BlockPos position) throws CommandSyntaxException {
        if (position == null) {
            if (context.getSource().getLevel() != level(context)) {
                throw error("Specify coordinates when selecting in another dimension.");
            }
            position = context.getSource().getPlayerOrException().blockPosition();
        }
        Selection selection = nativeSelection(context);
        selection.first = null;
        selection.second = null;
        Region.Point point = new Region.Point(position.getX(), position.getZ());
        if (selection.points.contains(point)) {
            throw error("This polygon point is already selected.");
        }
        if (selection.points.size() >= 1024) {
            throw error("A selection cannot exceed 1024 polygon points.");
        }
        selection.points.add(point);
        selection.minY = selection.minY == null ? position.getY() : Math.min(selection.minY, position.getY());
        selection.maxY = selection.maxY == null ? position.getY() : Math.max(selection.maxY, position.getY());
        return feedback(context, "Polygon point " + selection.points.size() + ": " + point.x() + ", " + point.z()
                + ". Set vertical bounds with /rg heights <minY> <maxY>.");
    }

    private static int heights(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Selection selection = nativeSelection(context);
        int first = IntegerArgumentType.getInteger(context, "minY");
        int second = IntegerArgumentType.getInteger(context, "maxY");
        selection.minY = Math.min(first, second);
        selection.maxY = Math.max(first, second);
        if (selection.first != null) {
            selection.first = new BlockPos(selection.first.getX(), selection.minY, selection.first.getZ());
        }
        if (selection.second != null) {
            selection.second = new BlockPos(selection.second.getX(), selection.maxY, selection.second.getZ());
        }
        return feedback(context, "Selection height: " + selection.minY + " to " + selection.maxY + ".");
    }

    private static Region selected(CommandContext<CommandSourceStack> context, String id) throws CommandSyntaxException {
        if (id.equalsIgnoreCase("__global__")) {
            return Region.global("__global__");
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        Selection nativeSelection = SELECTIONS.get(player.getUUID());
        if (nativeSelection != null && nativeSelection.dimension.equals(dimension(context))) {
            return nativeSelection.region(id);
        }
        Region worldEdit = WorldEditSelectionBridge.selection(player, level(context), id);
        if (worldEdit == null) {
            throw error("Select two corners with a wooden axe, /rg pos1 and /rg pos2, or a WorldEdit selection.");
        }
        return worldEdit;
    }

    private static int selectionInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Region selection = selected(context, "_selection");
        return feedback(context, "Selection: " + selection.type() + "; " + bounds(selection) + "; volume " + selection.volume()
                + " blocks in " + dimension(context) + ".");
    }

    private static int define(CommandContext<CommandSourceStack> context, boolean claim, String owners) throws IOException, CommandSyntaxException {
        return define(context,claim,owners,false);
    }
    private static int define(CommandContext<CommandSourceStack> context, boolean claim, String owners, boolean global) throws IOException, CommandSyntaxException {
        claim = claim || !operator(context.getSource());
        String id = StringArgumentType.getString(context, "id").toLowerCase(Locale.ROOT);
        if (regions(context).containsKey(id)) {
            throw error("Region '" + id + "' already exists. Use /rg redefine " + id + ".");
        }
        Region region = global ? Region.global(id) : selected(context, id);
        if (owners != null) {
            addDomain(context, region.owners(), owners, false);
        }
        if (claim) {
            ServerPlayer player = context.getSource().getPlayerOrException();
            var service = WorldGuardMod.service();
            String dimension = dimension(context);
            validateClaim(context, region, null);
            String parent = service.config().string(dimension, "regions.set-parent-on-claim", "").toLowerCase(Locale.ROOT);
            if (!parent.isBlank() && regions(context).containsKey(parent)) region.parent(parent);
            region.owners().uuids().add(player.getUUID());
        }
        Map<String, Region> regions = regions(context);
        persist(context, () -> regions.put(id, region), () -> regions.remove(id));
        return feedback(context, "Region '" + id + "' created and saved in " + dimension(context) + "."
                + domainGroupNotice(region.owners()));
    }

    private static void validateClaim(CommandContext<CommandSourceStack> context, Region region, String replacing) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var service = WorldGuardMod.service();
        String dimension = dimension(context);
        if (region.type().equals("global")) throw error("The global region cannot be claimed.");
        RegionQuery ownership = ownership(context, player);
        boolean overlaps = false;
        for (Region other : regions(context).values()) {
            if (!other.type().equals("global") && other.intersects(region)) {
                overlaps = true;
                if (!other.id().equals(replacing) && !ownership.isOwner(other))
                    throw error("Selection overlaps region '" + other.id() + "' owned by another player.");
            }
        }
        if (!overlaps && service.config().bool(dimension, "regions.claim-only-inside-existing-regions", false))
            throw error("Claims must overlap an existing region you own.");
        if (!service.hasPermission(player, "worldguard.region.unlimited")) {
            if (region.type().equals("poly2d")) throw error("Polygonal claims require worldguard.region.unlimited; select a cuboid with a wooden axe.");
            int maximum = service.config().integer(dimension, "regions.max-claim-volume", 30000);
            if (maximum >= 0 && region.volume() > maximum) throw error("Claim volume exceeds the configured maximum of " + maximum + " blocks.");
            if (replacing == null) {
                int count = service.maximumClaims(player, dimension);
                long owned = regions(context).values().stream().filter(r -> !r.type().equals("global") && ownership.isOwner(r)).count();
                if (count >= 0 && owned >= count) throw error("You already own the configured maximum of " + count + " regions.");
            }
        }
    }

    private static int defineGlobal(CommandContext<CommandSourceStack> context) throws IOException, CommandSyntaxException {
        Map<String, Region> regions = regions(context);
        if (regions.containsKey("__global__")) {
            return info(context, regions.get("__global__"));
        }
        persist(context, () -> regions.put("__global__", Region.global("__global__")), () -> regions.remove("__global__"));
        return feedback(context, "Global region created and saved in " + dimension(context) + ".");
    }

    private static int redefine(CommandContext<CommandSourceStack> context) throws IOException, CommandSyntaxException {
        Region previous = existing(context);
        requireOwner(context, previous);
        if (previous.id().equals("__global__")) {
            throw error("The global region has no editable boundaries.");
        }
        Region replacement = selected(context, previous.id());
        if (!operator(context.getSource())) validateClaim(context, replacement, previous.id());
        copyProperties(previous, replacement);
        Map<String, Region> regions = regions(context);
        persist(context, () -> regions.put(previous.id(), replacement), () -> regions.put(previous.id(), previous));
        return feedback(context, "Region '" + replacement.id() + "' boundaries updated and saved; flags, domains, parent and priority preserved.");
    }
    private static int redefineGlobal(CommandContext<CommandSourceStack> context) throws IOException, CommandSyntaxException {
        Region previous=existing(context),replacement=Region.global(previous.id()); copyProperties(previous,replacement);
        Map<String,Region> regions=regions(context);
        persist(context,()->regions.put(previous.id(),replacement),()->regions.put(previous.id(),previous));
        return feedback(context,"Region '" + previous.id() + "' converted to a nonphysical inheritance template.");
    }
    private static int removeTree(CommandContext<CommandSourceStack> context) throws IOException, CommandSyntaxException {
        Region region=existing(context); Map<String,Region> regions=regions(context),previous=new LinkedHashMap<>(regions);
        requireOwner(context, region);
        Set<String> removed=new LinkedHashSet<>(); removed.add(region.id());
        boolean changed;
        do { changed=false; for(Region child : regions.values()) if(removed.contains(child.parent()) && removed.add(child.id())) changed=true; } while(changed);
        for (String id : removed) requireOwner(context, regions.get(id));
        persist(context,()->removed.forEach(regions::remove),()->{regions.clear();regions.putAll(previous);});
        return feedback(context,"Removed regions and descendants: " + String.join(", ",removed));
    }

    private static void copyProperties(Region source, Region target) {
        target.priority(source.priority());
        target.parent(source.parent());
        target.flags().putAll(source.flags());
        copyDomain(source.owners(), target.owners());
        copyDomain(source.members(), target.members());
    }

    private static void copyDomain(Domain source, Domain target) {
        target.uuids().addAll(source.uuids());
        target.players().addAll(source.players());
        target.groups().addAll(source.groups());
    }

    private static int remove(CommandContext<CommandSourceStack> context, boolean force) throws IOException, CommandSyntaxException {
        Region region = existing(context);
        requireOwner(context, region);
        Map<String, Region> regions = regions(context);
        List<Region> children = regions.values().stream().filter(child -> region.id().equals(child.parent())).toList();
        if (force) for (Region child : children) requireOwner(context, child);
        if (!children.isEmpty() && !force) {
            throw error("Region has children: " + children.stream().map(Region::id).collect(Collectors.joining(", "))
                    + ". Use /rg remove " + region.id() + " force to detach them.");
        }
        persist(context, () -> {
            regions.remove(region.id());
            children.forEach(child -> child.parent(null));
        }, () -> {
            regions.put(region.id(), region);
            children.forEach(child -> child.parent(region.id()));
        });
        return feedback(context, "Region '" + region.id() + "' removed and saved. Detached children: " + children.size() + ".");
    }

    private static int info(CommandContext<CommandSourceStack> context, Region region) {
        return feedback(context, "Region " + region.id() + " (" + region.type() + ") in " + dimension(context)
                + "; " + bounds(region) + "; volume=" + region.volume() + "; priority=" + region.priority()
                + "; parent=" + Objects.toString(region.parent(), "none") + "\nOwners: " + domainText(region.owners())
                + "\nMembers: " + domainText(region.members()) + "\nFlags: " + region.flags());
    }

    private static String bounds(Region region) {
        return region.type().equals("global") ? "entire dimension" : "(" + region.minX() + ", " + region.minY() + ", " + region.minZ()
                + ") - (" + region.maxX() + ", " + region.maxY() + ", " + region.maxZ() + ")";
    }

    private static String domainText(Domain domain) {
        List<String> values = new ArrayList<>();
        domain.uuids().forEach(uuid -> values.add(uuid.toString()));
        values.addAll(domain.players());
        domain.groups().forEach(group -> values.add("g:" + group));
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static int infoHere(CommandContext<CommandSourceStack> context, boolean select) throws CommandSyntaxException {
        if (level(context) != context.getSource().getLevel()) {
            throw error("Specify a region ID when querying another dimension.");
        }
        BlockPos position = BlockPos.containing(context.getSource().getPosition());
        List<Region> applicable = regions(context).values().stream()
                .filter(region -> !region.type().equals("global") && region.contains(position.getX(), position.getY(), position.getZ()))
                .sorted(Comparator.<Region>comparingInt(Region::priority).reversed().thenComparing(Region::id)).toList();
        if (applicable.isEmpty()) {
            Region global = regions(context).get("__global__");
            if (!select && global != null) {
                return info(context, global);
            }
            throw error("No region at this position.");
        }
        if (select) {
            return select(context, applicable.getFirst());
        }
        applicable.forEach(region -> info(context, region));
        return applicable.size();
    }

    private static int select(CommandContext<CommandSourceStack> context, Region region) throws CommandSyntaxException {
        if (region.type().equals("global")) {
            throw error("The global region cannot be selected.");
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        Selection selection = new Selection(dimension(context));
        selection.first = new BlockPos(region.minX(), region.minY(), region.minZ());
        selection.second = new BlockPos(region.maxX(), region.maxY(), region.maxZ());
        selection.points.addAll(region.points());
        selection.minY = region.minY();
        selection.maxY = region.maxY();
        SELECTIONS.put(player.getUUID(), selection);
        WorldEditSelectionBridge.select(player, level(context), region);
        return feedback(context, "Region '" + region.id() + "' selected" + (WorldEditSelectionBridge.available() ? " in WorldEdit and native selection." : "."));
    }

    private static int list(CommandContext<CommandSourceStack> context, int page, String owner) throws CommandSyntaxException {
        UUID uuid = owner == null ? null : knownUuid(context, owner);
        String ownerName = owner == null ? null : owner.toLowerCase(Locale.ROOT);
        List<Region> regions = regions(context).values().stream().filter(region -> owner == null
                        || region.owners().players().stream().anyMatch(ownerName::equalsIgnoreCase) || uuid != null && region.owners().uuids().contains(uuid))
                .sorted(Comparator.comparing(Region::id)).toList();
        int pages = Math.max(1, (regions.size() + 9) / 10);
        if (page > pages) {
            throw error("Page must be between 1 and " + pages + ".");
        }
        String listing = regions.stream().skip((long) (page - 1) * 10).limit(10)
                .map(region -> region.id() + " [" + region.type() + ", priority " + region.priority() + "]")
                .collect(Collectors.joining("\n"));
        return feedback(context, "Regions in " + dimension(context) + ": " + regions.size() + "; page " + page + "/" + pages
                + (listing.isEmpty() ? "\nNone." : "\n" + listing));
    }
    private static int filteredList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<CommandWords.Word> words=CommandWords.parse(StringArgumentType.getString(context,"options"));
        String owner=null,filter=null; boolean nameOnly=false,selection=false; int page=1;
        for(int i=0;i<words.size();i++) {
            String option=words.get(i).text();
            if(option.equals("-p") || option.equals("-i")) {
                if(i+1==words.size()) throw error("Missing value after " + option);
                String value=words.get(++i).text(); if(option.equals("-p")) owner=value; else filter=value;
            } else if(option.equals("-n")) nameOnly=true;
            else if(option.equals("-s")) selection=true;
            else try { page=Math.max(1,Integer.parseInt(option)); } catch(NumberFormatException e) { throw error("Unknown list option " + option); }
        }
        Region shape=selection ? selected(context,"_list_filter") : null;
        UUID uuid=owner==null || nameOnly ? null : knownUuid(context,owner);
        String requestedOwner=owner,substring=filter;
        List<Region> matched=regions(context).values().stream().filter(r->shape==null || r.intersects(shape))
                .filter(r->substring==null || r.id().contains(substring))
                .filter(r->requestedOwner==null || r.owners().contains(uuid,requestedOwner) || r.members().contains(uuid,requestedOwner)
                        || requestedOwner.startsWith("g:") && (r.owners().groups().stream().anyMatch(requestedOwner.substring(2)::equalsIgnoreCase)
                        || r.members().groups().stream().anyMatch(requestedOwner.substring(2)::equalsIgnoreCase)))
                .sorted(Comparator.comparing((Region r)->r.id().equals("__global__") ? "" : r.id())).toList();
        int pages=Math.max(1,(matched.size()+9)/10); if(page>pages) throw error("Page must be between 1 and " + pages);
        String listing=matched.stream().skip((long)(page-1)*10).limit(10).map(r->r.id()+" ["+r.type()+", priority "+r.priority()+"]").collect(Collectors.joining("\n"));
        return feedback(context,"Regions in "+dimension(context)+": "+matched.size()+"; page "+page+"/"+pages+(listing.isEmpty() ? "\nNone." : "\n"+listing));
    }

    private static int flag(CommandContext<CommandSourceStack> context, String input) throws IOException, CommandSyntaxException {
        String id = StringArgumentType.getString(context, "id").toLowerCase(Locale.ROOT);
        Map<String, Region> regions = regions(context);
        boolean newGlobal = id.equals("__global__") && !regions.containsKey(id);
        Region region = newGlobal ? Region.global(id) : existing(context);
        requireOwner(context, region);
        String requestedFlag = StringArgumentType.getString(context, "flag");
        String flag = FlagRegistry.canonicalName(requestedFlag);
        if (flag == null) {
            throw error("Unknown flag '" + requestedFlag + "'. Use /rg flags.");
        }
        String group = null;
        String value = input;
        boolean empty = false;
        Integer helpPage=null;
        if (input != null) {
            // WG's -g option is accepted before or after the flag value.
            List<CommandWords.Word> tokens = CommandWords.parse(input);
            List<String> remaining = new ArrayList<>();
            for (int index = 0; index < tokens.size(); index++) {
                if (!tokens.get(index).quoted() && tokens.get(index).text().equals("-g")) {
                    if (group != null || index + 1 >= tokens.size()) {
                        throw error("Usage: /rg flag <id> <flag> [-g all|members|nonmembers|owners|nonowners|none] [value].");
                    }
                    group = tokens.get(++index).text();
                } else if (!tokens.get(index).quoted() && tokens.get(index).text().equals("-e")) {
                    empty = true;
                } else if(!tokens.get(index).quoted() && tokens.get(index).text().equals("-h")) {
                    if(++index>=tokens.size()) throw error("Specify a page after -h"); helpPage=Integer.parseInt(tokens.get(index).text());
                } else {
                    remaining.add(tokens.get(index).text());
                }
            }
            value = String.join(" ", remaining);
            if (empty && !remaining.isEmpty()) {
                throw error("Use -e without another flag value to set an empty value.");
            }
            if (value.isBlank() && !empty) {
                value = null;
            }
        }
        String groupFlag = flag + "-group";
        if (group != null && (flag.endsWith("-group") || !FlagRegistry.contains(groupFlag))) {
            throw error("This flag does not support region groups.");
        }
        Object parsed = value == null ? null : parseFlag(context, flag, value);
        if (parsed instanceof Map<?, ?> location && FlagRegistry.type(flag) == FlagRegistry.Type.LOCATION
                && WorldGuardMod.service().config().bool(dimension(context), "regions.location-flags-only-inside-regions", false)) {
            BlockPos point = BlockPos.containing(((Number) location.get("x")).doubleValue(), ((Number) location.get("y")).doubleValue(), ((Number) location.get("z")).doubleValue());
            boolean inside = dimension(context).equals(WorldGuardMod.service().resolveWorld(location.get("world").toString()))
                    && region.contains(point.getX(), point.getY(), point.getZ());
            if (!inside && !(context.getSource().getEntity() instanceof ServerPlayer player && WorldGuardMod.service().bypass(player))
                    && context.getSource().getEntity() != null) throw error("Location flags must remain inside the region boundaries.");
        }
        boolean hasValue = value != null;
        Object parsedGroup = group == null ? null : FlagRegistry.parse(groupFlag, group);
        Map<String, Object> previous = new LinkedHashMap<>(region.flags());
        String selectedGroup = group;
        persist(context, () -> {
            if (newGlobal) {
                regions.put(id, region);
            }
            if (hasValue) {
                if (parsed == null) {
                    region.flags().remove(flag);
                    if (selectedGroup == null) {
                        region.flags().remove(groupFlag);
                    }
                } else {
                    region.flags().put(flag, parsed);
                }
            } else if (selectedGroup == null) {
                region.flags().remove(flag);
                region.flags().remove(groupFlag);
            }
            if (selectedGroup != null) {
                if (Objects.equals(parsedGroup, FlagRegistry.defaultGroup(flag))) {
                    region.flags().remove(groupFlag);
                } else {
                    region.flags().put(groupFlag, parsedGroup);
                }
            }
        }, () -> {
            region.flags().clear();
            region.flags().putAll(previous);
            if (newGlobal) {
                regions.remove(id);
            }
        });
        if(helpPage!=null) return flagList(context,id,helpPage);
        return feedback(context, "Region '" + id + "': " + flag
                + (!hasValue && group != null ? " value unchanged" : parsed == null ? " cleared" : " = " + parsed)
                + (group == null ? "" : "; group = " + parsedGroup) + ". Saved.");
    }

    private static Object parseFlag(CommandContext<CommandSourceStack> context, String flag, String value) throws CommandSyntaxException {
        if ((flag.equals("teleport") || flag.equals("spawn")) && value.equalsIgnoreCase("here")) {
            if (context.getSource().getLevel() != level(context)) {
                throw error("The 'here' location must be in the command's dimension.");
            }
            ServerPlayer player = context.getSource().getPlayerOrException();
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("world", dimension(context));
            location.put("x", player.getX());
            location.put("y", player.getY());
            location.put("z", player.getZ());
            location.put("yaw", player.getYRot());
            location.put("pitch", player.getXRot());
            return location;
        }
        Object parsed = FlagRegistry.parse(flag, value);
        if ((flag.equals("teleport") || flag.equals("spawn")) && parsed instanceof Map<?, ?> map) {
            Map<String, Object> location = new LinkedHashMap<>();
            map.forEach((key, child) -> location.put(key.toString(), child));
            if (Objects.toString(location.get("world"), "").isBlank()) {
                location.put("world", dimension(context));
            }
            return location;
        }
        return parsed;
    }

    private static int priority(CommandContext<CommandSourceStack> context) throws IOException, CommandSyntaxException {
        Region region = existing(context);
        int previous = region.priority();
        int next = IntegerArgumentType.getInteger(context, "priority");
        persist(context, () -> region.priority(next), () -> region.priority(previous));
        return feedback(context, "Region '" + region.id() + "' priority set to " + next + ". Saved.");
    }

    private static int parent(CommandContext<CommandSourceStack> context, String input) throws IOException, CommandSyntaxException {
        Region region = existing(context);
        String next = input == null ? null : input.toLowerCase(Locale.ROOT);
        if (next != null) {
            Region candidate = regions(context).get(next);
            if (candidate == null) {
                throw error("Parent region '" + next + "' does not exist.");
            }
            Set<String> seen = new LinkedHashSet<>();
            seen.add(region.id());
            while (candidate != null) {
                if (!seen.add(candidate.id())) {
                    throw error("The requested parent would create an inheritance cycle.");
                }
                candidate = candidate.parent() == null ? null : regions(context).get(candidate.parent());
            }
        }
        String previous = region.parent();
        persist(context, () -> region.parent(next), () -> region.parent(previous));
        return feedback(context, "Region '" + region.id() + "' parent " + (next == null ? "cleared" : "set to '" + next + "'") + ". Saved.");
    }

    private static int domain(CommandContext<CommandSourceStack> context, boolean owners, boolean remove, String input) throws IOException, CommandSyntaxException {
        Region region = existing(context);
        requireOwner(context, region);
        Domain domain = owners ? region.owners() : region.members();
        Domain previous = new Domain();
        copyDomain(domain, previous);
        // Parse into a detached domain so malformed input cannot partly modify a region.
        Domain next = new Domain();
        copyDomain(domain, next);
        addDomain(context, next, input, remove);
        persist(context, () -> replaceDomain(domain, next), () -> replaceDomain(domain, previous));
        return feedback(context, "Region '" + region.id() + "' " + (owners ? "owners" : "members") + ": " + domainText(domain) + ". Saved."
                + domainGroupNotice(domain));
    }

    private static String domainGroupNotice(Domain domain) {
        return domain.groups().isEmpty() ? "" : " Group membership uses vanilla operator groups or a native RegionGroupEvent provider.";
    }

    private static void replaceDomain(Domain target, Domain source) {
        target.uuids().clear();
        target.players().clear();
        target.groups().clear();
        copyDomain(source, target);
    }

    private static void addDomain(CommandContext<CommandSourceStack> context, Domain domain, String input, boolean remove) throws CommandSyntaxException {
        String[] tokens = input.trim().split("[\\s,]+");
        boolean nameOnly = java.util.Arrays.asList(tokens).contains("-n");
        for (String token : tokens) {
            if (token.equals("-n")) {
                continue;
            }
            if (token.equals("-a")) {
                if (!remove) {
                    throw error("-a is only accepted by removeowner and removemember.");
                }
                domain.uuids().clear();
                domain.players().clear();
                domain.groups().clear();
                continue;
            }
            if (token.startsWith("g:") || token.startsWith("group:")) {
                String group = token.substring(token.indexOf(':') + 1).toLowerCase(Locale.ROOT);
                if (group.isBlank()) {
                    throw error("Group name cannot be empty.");
                }
                if (remove) {
                    domain.groups().remove(group);
                } else {
                    domain.groups().add(group);
                }
                continue;
            }
            UUID uuid = null;
            try {
                uuid = UUID.fromString(token);
            } catch (IllegalArgumentException ignored) {
                if (!token.matches("[A-Za-z0-9_]{1,16}")) {
                    throw error("Invalid player name or UUID: " + token + ". Use g:<group> for a group.");
                }
            }
            String name = token.toLowerCase(Locale.ROOT);
            if (uuid == null && !nameOnly) {
                uuid = knownUuid(context, token);
            }
            if (remove) {
                domain.players().remove(name);
                if (uuid != null) {
                    domain.uuids().remove(uuid);
                }
            } else if (uuid != null && !nameOnly) {
                domain.uuids().add(uuid);
            } else {
                domain.players().add(name);
            }
        }
    }

    private static UUID knownUuid(CommandContext<CommandSourceStack> context, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayerByName(value);
            if (player != null) {
                return player.getUUID();
            }
            // Read the vanilla cache without asking Mojang or blocking the server on a lookup.
            Path cache = FMLPaths.GAMEDIR.get().resolve("usercache.json");
            if (Files.isRegularFile(cache)) {
                try (Reader reader = Files.newBufferedReader(cache)) {
                    for (var element : JsonParser.parseReader(reader).getAsJsonArray()) {
                        var object = element.getAsJsonObject();
                        if (object.has("name") && object.has("uuid") && value.equalsIgnoreCase(object.get("name").getAsString())) {
                            return UUID.fromString(object.get("uuid").getAsString());
                        }
                    }
                } catch (IOException | RuntimeException ignoredCacheFailure) {
                    // Offline names remain valid domain entries if the optional cache is unavailable.
                }
            }
            return null;
        }
    }

    private static int save(CommandContext<CommandSourceStack> context) throws IOException {
        WorldGuardMod.service().repository().save(dimension(context));
        return feedback(context, "Regions saved in " + dimension(context) + ".");
    }

    private static int reload(CommandContext<CommandSourceStack> context) throws IOException {
        WorldGuardMod.service().repository().reload(dimension(context));
        WorldGuardMod.service().flushSessions();
        return feedback(context, "Regions reloaded in " + dimension(context) + ".");
    }

    private static int bypass(CommandContext<CommandSourceStack> context, Boolean desired) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean current = WorldGuardMod.service().bypass(player);
        boolean next = desired == null ? !current : desired;
        if (current != next) {
            next = WorldGuardMod.service().toggleBypass(player);
        }
        return feedback(context, "WorldGuard bypass " + (next ? "enabled" : "disabled") + " for " + player.getGameProfile().getName() + ".");
    }

    private static int teleport(CommandContext<CommandSourceStack> context, String mode) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Region region = existing(context);
        if (!operator(context.getSource()) && (region.type().equals("global") || !ownership(context, player).isMember(region)))
            throw error("You must be an owner or member of region '" + region.id() + "' to teleport to it.");
        ServerLevel destination = level(context);
        double x, y, z;
        float yaw = player.getYRot(), pitch = player.getXRot();
        if (mode.equals("center")) {
            if (region.type().equals("global")) {
                throw error("The global region has no center.");
            }
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                throw error("Center teleport requires spectator mode, because the destination can be inside blocks.");
            }
            x = ((long) region.minX() + region.maxX()) / 2d + .5d;
            y = ((long) region.minY() + region.maxY()) / 2d + .5d;
            z = ((long) region.minZ() + region.maxZ()) / 2d + .5d;
        } else {
            Object value = inheritedValue(context, region, player, mode.equals("spawn") ? "spawn" : "teleport");
            if (!(value instanceof Map<?, ?> location)) {
                throw error("Region '" + region.id() + "' has no " + mode + " point for this player. Set /rg flag "
                        + region.id() + " " + mode + " here.");
            }
            x = number(location.get("x"));
            y = number(location.get("y"));
            z = number(location.get("z"));
            yaw = (float) number(location.containsKey("yaw") ? location.get("yaw") : 0);
            pitch = (float) number(location.containsKey("pitch") ? location.get("pitch") : 0);
            String world = Objects.toString(location.get("world"), "");
            if (!world.isBlank()) {
                String mapped = WorldGuardMod.service().config().migrationWorlds().getOrDefault(world, world);
                mapped = switch (mapped) {
                    case "world" -> "minecraft:overworld";
                    case "world_nether" -> "minecraft:the_nether";
                    case "world_the_end" -> "minecraft:the_end";
                    default -> mapped;
                };
                ResourceLocation id = ResourceLocation.tryParse(mapped);
                if (id == null) {
                    throw error("Invalid destination dimension: " + world + ". Reset the location flag with 'here'.");
                }
                destination = context.getSource().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
                if (destination == null) {
                    throw error("Destination dimension is not loaded: " + mapped + ".");
                }
            }
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || Math.abs(x) > 30_000_000d || Math.abs(z) > 30_000_000d || Math.abs(y) > 20_000_000d) {
            throw error("Teleport point is outside Minecraft's coordinate limits.");
        }
        if (!destination.getWorldBorder().isWithinBounds(BlockPos.containing(x, y, z))) {
            throw error("Teleport point is outside the world border.");
        }
        if (!WorldGuardMod.service().sessions().canTravel(player, destination, BlockPos.containing(x, y, z), true)) {
            throw error("Teleport denied by region entry/exit protection. Change the relevant flags or enable /rg bypass on.");
        }
        player.teleportTo(destination, x, y, z, yaw, pitch);
        Object message = inheritedValue(context, region, player, "teleport-message");
        return feedback(context, Objects.toString(message, "Teleported to region '%id%'.").replace("%id%", region.id()));
    }

    private static double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(Objects.toString(value, "0"));
    }

    private static Object inheritedValue(CommandContext<CommandSourceStack> context, Region region, ServerPlayer player, String flag) {
        List<Region> ancestry = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Region current = region; current != null && seen.add(current.id());
             current = current.parent() == null ? null : regions(context).get(current.parent())) {
            ancestry.add(current);
        }
        int x = region.type().equals("global") ? 0 : region.type().equals("poly2d") ? region.points().getFirst().x() : region.minX();
        int z = region.type().equals("global") ? 0 : region.type().equals("poly2d") ? region.points().getFirst().z() : region.minZ();
        int y = region.type().equals("global") ? 0 : region.minY();
        return new RegionQuery(ancestry, player.getUUID(), player.getGameProfile().getName()).value(x, y, z, flag);
    }

    private static Path importRoot() {
        return WorldGuardMod.service().directory().resolve("import").toAbsolutePath().normalize();
    }

    private static Path importFile(CommandContext<CommandSourceStack> context) throws IOException, CommandSyntaxException {
        String input = StringArgumentType.getString(context, "file");
        Path relative = Path.of(input);
        Path root = importRoot();
        Path source = root.resolve(relative).normalize();
        if (relative.isAbsolute() || !source.startsWith(root)) {
            throw error("Import files must be inside " + root + ".");
        }
        if (!Files.isRegularFile(source)) {
            throw error("Import file does not exist. Place it under " + root + " and provide its relative filename.");
        }
        if (!source.toRealPath().startsWith(root.toRealPath())) {
            throw error("Import file resolves outside the import directory.");
        }
        if (Files.size(source) > 16L * 1024 * 1024) {
            throw error("YAML import file exceeds 16 MiB.");
        }
        return source;
    }

    private static CompletableFuture<Suggestions> importSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        Path root = importRoot();
        if (!Files.isDirectory(root)) {
            return builder.buildFuture();
        }
        try (var paths = Files.walk(root, 6)) {
            List<String> names = paths.filter(Files::isRegularFile).limit(1000)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .filter(name -> name.endsWith(".yml") || name.endsWith(".yaml")).sorted().toList();
            return SharedSuggestionProvider.suggest(names, builder);
        } catch (IOException exception) {
            return builder.buildFuture();
        }
    }

    private static int importYaml(CommandContext<CommandSourceStack> context, boolean replace) throws IOException, CommandSyntaxException {
        Path source = importFile(context);
        int imported = WorldGuardMod.service().repository().importYaml(source, dimension(context), replace);
        WorldGuardMod.service().flushSessions();
        return feedback(context, "Imported and saved " + imported + " regions in " + dimension(context)
                + (replace ? ". Previous region set replaced." : ". Existing IDs preserved; duplicate imported IDs skipped."));
    }

    private static int importConfiguration(CommandContext<CommandSourceStack> context, boolean forDimension) throws IOException, CommandSyntaxException {
        Path source = importFile(context);
        String target = forDimension ? dimension(context) : null;
        WorldGuardMod.service().importConfiguration(source, target);
        return feedback(context, "WorldGuard configuration imported and reloaded" + (target == null ? "." : " for " + target + "."));
    }

    private static int migrateUuid(CommandContext<CommandSourceStack> context) throws IOException {
        WorldGuardMod.service().beginUuidMigration(true);
        return feedback(context,"UUID migration requested for every stored dimension. Pending profile lookups: " + WorldGuardMod.service().uuidLookups() + ". Completion is written to the server log.");
    }

    private static int flagList(CommandContext<CommandSourceStack> context,String id,int page) throws IOException,CommandSyntaxException {
        Region region=id==null ? null : regions(context).get(Region.normalizeId(id));
        if(id!=null && region==null) throw error("Unknown region: " + id);
        List<String> names=FlagRegistry.names().stream().filter(name -> !name.endsWith("-group")).sorted().toList();
        int pages=Math.max(1,(names.size()+11)/12); if(page<1 || page>pages) throw error("Page must be 1.." + pages);
        var text=Component.literal((id==null ? "Registered flags" : "Flags of " + id) + "; page " + page + "/" + pages + "\n");
        for(String name : names.stream().skip((long)(page-1)*12).limit(12).toList()) {
            Object value=region==null ? FlagRegistry.defaultValue(name) : region.flags().get(name);
            var line=Component.literal(name + " = " + (value==null ? "unset" : value) + " [" + FlagRegistry.type(name) + "]\n");
            if(region!=null && (operator(context.getSource()) || !region.type().equals("global")
                    && ownership(context,context.getSource().getPlayerOrException()).isOwner(region))) {
                String prefix="/rg in " + dimension(context) + " flag " + id + " " + name;
                String command=prefix + (FlagRegistry.isState(name) ? ("allow".equals(value) ? " deny" : " allow") : " ");
                line.withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(FlagRegistry.isState(name) ? net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND : net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,command))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,Component.literal("Change " + name))));
            }
            text.append(line);
        }
        if(page<pages) text.append(Component.literal("[Next page]").withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,"/rg flags -w " + dimension(context) + " -p " + (page+1) + (id==null ? "" : " " + id)))));
        context.getSource().sendSuccess(()->text,false); return 1;
    }

    private static int migrateHeights(CommandContext<CommandSourceStack> context) throws IOException {
        Map<String, Region> regions = regions(context);
        Map<String, Region> previous = new LinkedHashMap<>(regions);
        Map<String, Region> replacement = new LinkedHashMap<>();
        int converted = 0;
        for (Region region : regions.values()) {
            Region next = region;
            if (!region.type().equals("global") && region.minY() == 0 && region.maxY() == 255) {
                int minY = level(context).getMinBuildHeight();
                int maxY = level(context).getMaxBuildHeight() - 1;
                next = region.type().equals("poly2d") ? Region.polygon(region.id(), region.points(), minY, maxY)
                        : Region.cuboid(region.id(), region.minX(), minY, region.minZ(), region.maxX(), maxY, region.maxZ());
                copyProperties(region, next);
                converted++;
            }
            replacement.put(next.id(), next);
        }
        persist(context, () -> {
            regions.clear();
            regions.putAll(replacement);
        }, () -> {
            regions.clear();
            regions.putAll(previous);
        });
        return feedback(context, "Expanded " + converted + " regions spanning legacy Y=0..255 to dimension height bounds. Saved.");
    }

    private static int report(CommandContext<CommandSourceStack> context) throws IOException {
        String worlds = WorldGuardMod.service().repository().allDimensions().stream().sorted()
                    .map(world -> {
                        try {
                            return world + "=" + WorldGuardMod.service().repository().regions(world).size();
                        } catch (IOException exception) {
                            return world + "=ERROR (" + exception.getMessage() + ")";
                        }
                    }).collect(Collectors.joining(", "));
        String summary="WorldGuard report: storage=" + WorldGuardMod.service().directory()
                + "; protection=" + (WorldGuardMod.service().healthy() ? "active" : "storage error; fail closed")
                + "; dimensions/regions=" + worlds + "; flags=" + FlagRegistry.names().size()
                + "; operator-level=" + WorldGuardMod.service().config().operatorLevel()
                + "; players=" + context.getSource().getServer().getPlayerCount()
                + "; WorldEdit=" + WorldEditSelectionBridge.available() + "; native selections=" + SELECTIONS.size() + ".";
        StringBuilder report=new StringBuilder(summary).append("\nGenerated: ").append(java.time.Instant.now()).append("\nJava: ").append(System.getProperty("java.version"))
                .append("\nOS: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch"))
                .append("\nStorage: ").append(WorldGuardMod.service().repository().isSql() ? "SQL" : "YAML").append("\nMods:\n");
        for(var mod : ModList.get().getMods()) report.append("  ").append(mod.getModId()).append(' ').append(mod.getVersion()).append('\n');
        if(context.getSource().getEntity() instanceof ServerPlayer player) for(Region region : WorldGuardMod.service().regions(player.serverLevel(),player.blockPosition()))
            report.append("Applicable region: ").append(region.id()).append(" priority=").append(region.priority()).append(" flags=").append(region.flags()).append('\n');
        report.append("\nConfiguration (credentials omitted):\n").append(new org.yaml.snakeyaml.Yaml().dump(WorldGuardMod.service().config().diagnosticSnapshot()));
        Path target=WorldGuardMod.service().directory().resolve("report.txt"); Files.writeString(target,report);
        return feedback(context,summary + " Report saved: " + target);
    }

    /** Drop server-scoped selections when a player disconnects or a server stops. */
    public static void logout(UUID playerId) {
        clearSelection(playerId);
    }

    static void clearSelection(UUID playerId) {
        SELECTIONS.remove(playerId);
    }

    public static void clearSelections() {
        SELECTIONS.clear();
    }

    private static final class Selection {
        private final String dimension;
        private BlockPos first;
        private BlockPos second;
        private final List<Region.Point> points = new ArrayList<>();
        private Integer minY;
        private Integer maxY;

        private Selection(String dimension) {
            this.dimension = dimension;
        }

        private Region region(String id) {
            if (!points.isEmpty()) {
                if (points.size() < 3 || minY == null || maxY == null) {
                    throw new IllegalArgumentException("A polygon needs at least 3 points and /rg heights <minY> <maxY>.");
                }
                return Region.polygon(id, new ArrayList<>(points), minY, maxY);
            }
            if (first == null || second == null) {
                throw new IllegalArgumentException("Set both /rg pos1 and /rg pos2.");
            }
            return Region.cuboid(id, first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ());
        }
    }
}
