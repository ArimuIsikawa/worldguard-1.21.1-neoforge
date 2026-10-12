package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject eggs before vanilla consumes them or commits spawner configuration. */
@Mixin(SpawnEggItem.class)
public abstract class SpawnEggMixin {
    private boolean worldguard$allowed(ServerLevel level,BlockPos pos,Player player,ItemStack item) {
        var service=WorldGuardMod.service(); if(service==null) return true;
        var type=((SpawnEggItem)(Object)this).getType(item);
        return service.buildPermission(level,player,"entity",BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath(),"place")
                && service.canUse(level,pos,player,"block-place") && ProtectionEvents.spawn(level,pos,type,null);
    }
    @Inject(method="useOn",at=@At("HEAD"),cancellable=true)
    private void worldguard$block(UseOnContext context,CallbackInfoReturnable<InteractionResult> callback) {
        if(!(context.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos=context.getClickedPos();
        if(!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.Spawner) && !level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()) pos=pos.relative(context.getClickedFace());
        if(!worldguard$allowed(level,pos,context.getPlayer(),context.getItemInHand())) callback.setReturnValue(InteractionResult.FAIL);
    }
    @Inject(method="use",at=@At("HEAD"),cancellable=true)
    private void worldguard$water(Level level,Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResultHolder<ItemStack>> callback) {
        if(!(level instanceof ServerLevel server)) return;
        var hit=Item.getPlayerPOVHitResult(level,player,ClipContext.Fluid.SOURCE_ONLY);
        if(hit.getType()==HitResult.Type.BLOCK && !worldguard$allowed(server,hit.getBlockPos(),player,player.getItemInHand(hand))) callback.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));
    }
}
