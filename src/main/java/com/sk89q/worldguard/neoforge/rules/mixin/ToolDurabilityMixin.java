package com.sk89q.worldguard.neoforge.rules.mixin;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class ToolDurabilityMixin {
    @Inject(method = "mineBlock", at = {@At("HEAD"), @At("RETURN")})
    private void worldguard$durability(Level level, BlockState state, BlockPos pos, Player player, CallbackInfo callback) {
        if (level instanceof ServerLevel server && WorldGuardMod.service() != null
                && !WorldGuardMod.service().config().bool(server.dimension().location().toString(), "protection.item-durability", true)) {
            ItemStack stack = (ItemStack) (Object) this;
            if (stack.isDamageableItem()) stack.setDamageValue(0);
        }
    }
}
