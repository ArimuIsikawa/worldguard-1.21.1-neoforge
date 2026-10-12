package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/** Retain the actual player through synchronous vanilla/mod item spawning. */
@Mixin(ItemStack.class)
public abstract class ItemSpawnCauseMixin {
    @WrapMethod(method="useOn")
    private InteractionResult worldguard$on(UseOnContext context, Operation<InteractionResult> original) {
        try(var scope=ProtectionHooks.itemScope(context.getPlayer())) { return original.call(context); }
    }
    @WrapMethod(method="use")
    private InteractionResultHolder<ItemStack> worldguard$use(Level level, Player player, InteractionHand hand, Operation<InteractionResultHolder<ItemStack>> original) {
        try(var scope=ProtectionHooks.itemScope(player)) { return original.call(level,player,hand); }
    }
}
