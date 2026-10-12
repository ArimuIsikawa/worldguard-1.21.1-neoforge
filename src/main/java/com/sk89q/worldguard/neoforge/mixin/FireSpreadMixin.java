package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireSpreadMixin {
    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void worldguard$burn(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face, CallbackInfo callback) {
        if (level instanceof ServerLevel server && com.sk89q.worldguard.neoforge.WorldGuardMod.service()!=null) {
            BlockPos origin=pos.relative(face);
            if(!ProtectionEvents.flag(server,pos,null,"fire-spread") || !com.sk89q.worldguard.neoforge.WorldGuardMod.service().canAutomate(server,origin,pos,"block-break",false)) {
                callback.cancel();
                if(server.getBlockState(origin).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) server.removeBlock(origin,false);
            }
        }
    }
}
