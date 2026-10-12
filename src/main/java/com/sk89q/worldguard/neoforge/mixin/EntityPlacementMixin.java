package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ArmorStandItem.class, HangingEntityItem.class})
public abstract class EntityPlacementMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void worldguard$place(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
        if (!(context.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = (Object) this instanceof ArmorStandItem ? new BlockPlaceContext(context).getClickedPos()
                : context.getClickedPos().relative(context.getClickedFace());
        if (!ProtectionEvents.use(level, pos, context.getPlayer(), "block-place")) callback.setReturnValue(InteractionResult.FAIL);
    }
}
