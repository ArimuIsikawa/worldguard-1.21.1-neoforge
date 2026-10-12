package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sk89q.worldguard.neoforge.protection.PortalCreationPlan;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Optional;

@Mixin(PortalForcer.class)
public abstract class PortalCreationMixin {
    @Shadow @Final protected ServerLevel level;
    @WrapMethod(method="createPortal")
    private Optional<BlockUtil.FoundRectangle> worldguard$plan(BlockPos position, Direction.Axis axis, Operation<Optional<BlockUtil.FoundRectangle>> original) {
        try (PortalCreationPlan plan = new PortalCreationPlan(level)) {
            Optional<BlockUtil.FoundRectangle> result = original.call(position,axis);
            if (result.isEmpty() || !plan.allowed()) return Optional.empty();
            plan.commit(); return result;
        }
    }
    @WrapOperation(method="createPortal",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean worldguard$setBlock(ServerLevel world, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        return PortalCreationPlan.record(world,pos,state,flags) || original.call(world,pos,state,flags);
    }
    @WrapOperation(method="createPortal",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean worldguard$setBlockAndUpdate(ServerLevel world, BlockPos pos, BlockState state, Operation<Boolean> original) {
        return PortalCreationPlan.record(world,pos,state,3) || original.call(world,pos,state);
    }
}
