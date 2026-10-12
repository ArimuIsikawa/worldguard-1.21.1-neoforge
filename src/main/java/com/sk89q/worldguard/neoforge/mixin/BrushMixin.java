package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BrushableBlockEntity.class)
public abstract class BrushMixin {
    @Inject(method = "brush", at = @At("HEAD"), cancellable = true)
    private void worldguard$brush(long tick, Player player, Direction direction, CallbackInfoReturnable<Boolean> callback) {
        BrushableBlockEntity block = (BrushableBlockEntity) (Object) this;
        if (block.getLevel() instanceof ServerLevel level && !ProtectionEvents.use(level, block.getBlockPos(), player, "build")) callback.setReturnValue(false);
    }
}
