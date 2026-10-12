package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SaplingBlock.class)
public abstract class TreeGrowthMixin {
    @WrapMethod(method="advanceTree")
    private void worldguard$tree(ServerLevel level,BlockPos pos,BlockState state,RandomSource random,Operation<Void> original) {
        try(var scope=ProtectionHooks.scope("tree-growth",ProtectionHooks.itemActor(),pos)) { original.call(level,pos,state,random); }
    }
}
