package com.sk89q.worldguard.neoforge.admin;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import com.sk89q.worldguard.neoforge.command.CommandWords;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Native event simulation; emits the final NeoForge result without committing a world action. */
public final class DebugCommands {
    private DebugCommands() { }
    public static int run(CommandContext<CommandSourceStack> context,String type,String input) throws CommandSyntaxException {
        var source=context.getSource(); String targetName=null; boolean fromTarget=false,console=false;
        for(var word : CommandWords.parse(input)) {
            if(word.text().equals("-t")) fromTarget=true; else if(word.text().equals("-s")) console=true;
            else if(targetName==null) targetName=word.text(); else throw error("Specify one player and optional -t/-s");
        }
        var player=targetName==null ? source.getPlayerOrException() : EntityArgument.player().parse(new StringReader(targetName)).findSinglePlayer(source);
        var trace=fromTarget ? player : source.getPlayerOrException();
        var level=trace.serverLevel(); var hit=trace.pick(20,1,false);
        if(!(hit instanceof BlockHitResult block) || hit.getType()!=HitResult.Type.BLOCK) throw error("Look at a block within 20 blocks; consoles must specify a player with -t");
        var pos=block.getBlockPos(); String result;
        switch(type) {
            case "testbreak" -> { var event=new BlockEvent.BreakEvent(level,pos,level.getBlockState(pos),player); NeoForge.EVENT_BUS.post(event); result="canceled=" + event.isCanceled(); }
            case "testplace" -> { var event=new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(),level,pos),level.getBlockState(pos.below()),player); NeoForge.EVENT_BUS.post(event); result="canceled=" + event.isCanceled(); }
            case "testinteract" -> { var event=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,block); NeoForge.EVENT_BUS.post(event); result="canceled=" + event.isCanceled() + ", useBlock=" + event.getUseBlock() + ", useItem=" + event.getUseItem(); }
            case "testdamage" -> {
                var start=trace.getEyePosition(); var end=start.add(trace.getViewVector(1).scale(20));
                var entityHit=ProjectileUtil.getEntityHitResult(trace,start,end,trace.getBoundingBox().expandTowards(end.subtract(start)).inflate(1),entity->entity!=player && entity.isPickable(),400);
                if(entityHit==null) throw error("Look at an entity within 20 blocks");
                var event=new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(player,entityHit.getEntity()); NeoForge.EVENT_BUS.post(event); result="canceled=" + event.isCanceled() + ", target=" + entityHit.getEntity().getType();
            }
            default -> throw error("Unknown debug event");
        }
        String report="WorldGuard NeoForge " + type + " at " + level.dimension().location() + " " + pos.toShortString() + ": " + result;
        source.sendSuccess(()->Component.literal(report),false); if(console) WorldGuardMod.LOGGER.info("{}",report); return 1;
    }
    private static CommandSyntaxException error(String message) { return new SimpleCommandExceptionType(Component.literal(message)).create(); }
}
