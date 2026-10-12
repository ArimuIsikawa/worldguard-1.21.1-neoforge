package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BoneMealItem.class)
public abstract class BonemealMixin {
    @WrapMethod(method = "applyBonemeal")
    private static boolean worldguard$growth(ItemStack stack, Level level, BlockPos pos, Player player, Operation<Boolean> original) {
        String flag = ProtectionHooks.naturalFlag(level.getBlockState(pos), true);
        if (flag == null) flag = "crop-growth";
        if (level instanceof ServerLevel server && (player != null && !ProtectionEvents.use(server, pos, player, "build")
                || !ProtectionEvents.flag(server, pos, player, flag))) return false;
        try (var scope = ProtectionHooks.scope(flag, player)) { return original.call(stack, level, pos, player); }
    }

    @WrapMethod(method = "growWaterPlant")
    private static boolean worldguard$water(ItemStack stack, Level level, BlockPos pos, Direction direction, Operation<Boolean> original) {
        if (level instanceof ServerLevel server && !ProtectionEvents.flag(server, pos, null, "crop-growth")) return false;
        try (var scope = ProtectionHooks.scope("crop-growth", null)) { return original.call(stack, level, pos, direction); }
    }
}
