package com.sk89q.worldguard.neoforge.admin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class AdministrationCommands {
    private AdministrationCommands() { }
    public static boolean operator(CommandSourceStack source) {
        var service = WorldGuardMod.service();
        return source.hasPermission(service == null ? 2 : service.config().operatorLevel());
    }
    public static boolean intensive(Entity entity) { return entity instanceof Mob || entity instanceof ItemEntity || entity instanceof Projectile; }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : List.of("god", "ungod", "heal", "slay", "wglocate")) {
            dispatcher.register(Commands.literal(name).requires(AdministrationCommands::operator)
                .executes(c -> players(c, name, List.of(c.getSource().getPlayerOrException())))
                .then(Commands.argument("players", EntityArgument.players()).executes(c -> players(c, name, EntityArgument.getPlayers(c, "players"))))
                .then(Commands.literal("-s").executes(c -> players(c,name,List.of(c.getSource().getPlayerOrException()),true))
                        .then(Commands.argument("players",EntityArgument.players()).executes(c -> players(c,name,EntityArgument.getPlayers(c,"players"),true)))));
        }
        // Minecraft owns /locate structure; extend it without replacing the vanilla tree.
        dispatcher.register(Commands.literal("locate").then(Commands.literal("player").requires(AdministrationCommands::operator)
            .then(Commands.argument("players", EntityArgument.players()).executes(c -> players(c, "wglocate", EntityArgument.getPlayers(c, "players"))))));
        dispatcher.register(Commands.literal("locate").requires(AdministrationCommands::operator).executes(c -> compass(c,null))
                .then(Commands.argument("target",EntityArgument.player()).executes(c -> compass(c,EntityArgument.getPlayer(c,"target")))));
        dispatcher.register(Commands.literal("stack").requires(AdministrationCommands::operator).executes(c -> stack(c, -1))
            .then(Commands.argument("size", IntegerArgumentType.integer(1)).executes(c -> stack(c, IntegerArgumentType.getInteger(c, "size")))));
        dispatcher.register(Commands.literal(";").requires(AdministrationCommands::operator).executes(c -> stack(c,-1)));
        for (String name : List.of("stopfire", "allowfire")) dispatcher.register(Commands.literal(name).requires(AdministrationCommands::operator)
            .executes(c -> fire(c, c.getSource().getLevel().dimension().location().toString(), name.equals("stopfire")))
            .then(Commands.argument("dimension", DimensionArgument.dimension()).executes(c -> fire(c,
                DimensionArgument.getDimension(c, "dimension").dimension().location().toString(), name.equals("stopfire")))));
        for (String name : List.of("halt-activity", "haltactivity", "stoplag")) {
            dispatcher.register(Commands.literal(name).requires(AdministrationCommands::operator)
                .executes(c -> feedback(c, "Use /" + name + " confirm to halt natural updates and remove loaded mobs, dropped items and projectiles; /" + name + " resume to resume."))
                .then(Commands.literal("confirm").executes(c -> halt(c, true)))
                .then(Commands.literal("resume").executes(c -> halt(c, false)))
                .then(Commands.literal("-c").executes(c -> halt(c, false)))
                .then(Commands.literal("-i").executes(c -> feedback(c, "Activity halted: " + WorldGuardMod.service().halted())))
                .then(Commands.literal("-s").executes(c -> feedback(c,"Use /" + name + " -s confirm or resume.",false))
                        .then(Commands.literal("confirm").executes(c -> halt(c,true,true)))
                        .then(Commands.literal("resume").executes(c -> halt(c,false,true)))
                        .then(Commands.literal("-i").executes(c -> feedback(c,"Activity halted: " + WorldGuardMod.service().halted(),false)))));
        }
        var wg = dispatcher.getRoot().getChild("worldguard");
        if (wg != null) {
            wg.addChild(Commands.literal("profile").requires(AdministrationCommands::operator).executes(c -> profile(c, ""))
                    .then(Commands.argument("options",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c -> profile(c,com.mojang.brigadier.arguments.StringArgumentType.getString(c,"options")))).build());
            wg.addChild(Commands.literal("stopprofile").requires(AdministrationCommands::operator).executes(AdministrationCommands::stopProfile).build());
        }
    }
    private static int feedback(CommandContext<CommandSourceStack> c, String text) {
        return feedback(c,text,true);
    }
    private static int feedback(CommandContext<CommandSourceStack> c,String text,boolean broadcast) {
        c.getSource().sendSuccess(() -> Component.literal(text), broadcast); return 1;
    }
    private static int players(CommandContext<CommandSourceStack> c, String command, Collection<ServerPlayer> players) {
        return players(c,command,players,false);
    }
    private static int players(CommandContext<CommandSourceStack> c, String command, Collection<ServerPlayer> players,boolean silent) {
        var service = WorldGuardMod.service();
        for (ServerPlayer player : players) {
            switch (command) {
                case "god" -> { service.god(player, true); player.clearFire(); }
                case "ungod" -> service.god(player, false);
                case "heal" -> { player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(20); player.clearFire(); }
                case "slay" -> { player.setHealth(0); player.die(player.damageSources().genericKill()); }
                case "wglocate" -> c.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + ": "
                    + player.serverLevel().dimension().location() + " " + player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ()), false);
            }
            if(!silent && !command.equals("wglocate") && c.getSource().getEntity()!=player) player.sendSystemMessage(Component.literal(command + " by " + c.getSource().getTextName() + "."));
        }
        return command.equals("wglocate") ? players.size() : feedback(c, command + ": " + players.size() + " player(s).");
    }
    private static int stack(CommandContext<CommandSourceStack> c, int requested) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        if(requested < 0) {
            var items=player.getInventory().items;
            for(int first=0;first<items.size();first++) {
                ItemStack target=items.get(first); if(target.isEmpty() || !target.isStackable()) continue;
                for(int next=first+1;next<items.size() && target.getCount()<target.getMaxStackSize();next++) {
                    ItemStack source=items.get(next); if(!ItemStack.isSameItemSameComponents(target,source)) continue;
                    int moved=Math.min(source.getCount(),target.getMaxStackSize()-target.getCount()); target.grow(moved); source.shrink(moved);
                }
            }
            player.inventoryMenu.broadcastChanges(); return feedback(c,"Inventory items compacted into stacks.");
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return feedback(c, "Hold an item first.");
        int limit = Math.min(requested < 0 ? held.getMaxStackSize() : requested, held.getMaxStackSize());
        for (int slot = 0; slot < player.getInventory().items.size() && held.getCount() < limit; slot++) {
            if (slot == player.getInventory().selected) continue;
            ItemStack item = player.getInventory().items.get(slot);
            if (!ItemStack.isSameItemSameComponents(held, item)) continue;
            int count = Math.min(item.getCount(), limit - held.getCount()); held.grow(count); item.shrink(count);
        }
        player.inventoryMenu.broadcastChanges();
        return feedback(c, "Items stacked: " + held.getCount() + ".");
    }
    private static int fire(CommandContext<CommandSourceStack> c, String world, boolean disable) {
        WorldGuardMod.service().fireDisabled(world, disable);
        return feedback(c, "Temporary fire-spread stop in " + world + ": " + disable + ".");
    }
    private static int compass(CommandContext<CommandSourceStack> context, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player=context.getSource().getPlayerOrException();
        WorldGuardMod.service().compassTarget(player,target == null ? null : target.blockPosition());
        return feedback(context,target == null ? "Compass reset to world spawn." : "Compass pointed at " + target.getGameProfile().getName() + ".");
    }
    private static int halt(CommandContext<CommandSourceStack> c, boolean value) {
        return halt(c,value,false);
    }
    private static int halt(CommandContext<CommandSourceStack> c, boolean value,boolean silent) {
        var service = WorldGuardMod.service(); service.halted(value); int removed = 0;
        if (value) for (var level : service.server().getAllLevels()) {
            List<Entity> entities = new ArrayList<>(); level.getAllEntities().forEach(entities::add);
            for (Entity entity : entities) if (intensive(entity)) { entity.discard(); removed++; }
        }
        return feedback(c, "Activity halted: " + value + "; entities removed: " + removed + ".",!silent);
    }
    private static int profile(CommandContext<CommandSourceStack> c, String input) {
        try {
            var words=com.sk89q.worldguard.neoforge.command.CommandWords.parse(input); int minutes=5,interval=20; String thread="Server thread"; boolean duration=false;
            for(int index=0;index<words.size();index++) {
                String option=words.get(index).text();
                if(option.equals("-i") || option.equals("-t")) {
                    if(++index>=words.size()) throw new IllegalArgumentException("Specify a value after " + option);
                    if(option.equals("-i")) interval=Integer.parseInt(words.get(index).text()); else thread=words.get(index).text();
                } else if(option.equals("-p")) throw new IllegalArgumentException("Profiles are saved locally; use /wg profile without -p");
                else if(!duration) { minutes=Integer.parseInt(option); duration=true; } else throw new IllegalArgumentException("Specify one duration in minutes");
            }
            var source=c.getSource();
            WorldGuardMod.service().profiler().start(minutes,interval,thread,file->source.getServer().execute(()->source.sendSuccess(()->Component.literal("Profile saved: " + file),false)),error->WorldGuardMod.LOGGER.error("Unable to write WorldGuard profile",error));
            return feedback(c,"Stack sampling started; /wg stopprofile saves the current profile early.");
        } catch(IllegalArgumentException | IllegalStateException error) { c.getSource().sendFailure(Component.literal(error.getMessage())); return 0; }
    }
    private static int stopProfile(CommandContext<CommandSourceStack> c) {
        try { return feedback(c,"Profile saved: " + WorldGuardMod.service().profiler().stop()); }
        catch(java.io.IOException | IllegalStateException error) { c.getSource().sendFailure(Component.literal(error.getMessage())); return 0; }
    }
}
