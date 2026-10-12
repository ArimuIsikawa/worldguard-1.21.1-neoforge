package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sk89q.worldguard.neoforge.protection.PortalCreationPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NetherPortalBlock.class)
public abstract class PortalActorMixin {
    @WrapMethod(method="getExitPortal")
    private DimensionTransition worldguard$actor(ServerLevel level, Entity entity, BlockPos pos, BlockPos exit, boolean nether, WorldBorder border, Operation<DimensionTransition> original) {
        try (var scope = new PortalCreationPlan.ActorScope(entity)) { return original.call(level,entity,pos,exit,nether,border); }
    }
}
