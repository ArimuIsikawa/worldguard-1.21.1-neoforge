package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockAttachedEntity.class)
public abstract class HangingPhysicsMixin {
    private boolean worldguard$removable() {
        BlockAttachedEntity entity = (BlockAttachedEntity) (Object) this;
        if (!(entity.level() instanceof ServerLevel level)) return true;
        String flag = entity instanceof Painting ? "entity-painting-destroy" : entity instanceof ItemFrame ? "entity-item-frame-destroy" : null;
        return flag == null || ProtectionEvents.flag(level, entity.blockPosition(), null, flag);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/decoration/BlockAttachedEntity;survives()Z"))
    private boolean worldguard$survival(BlockAttachedEntity entity, Operation<Boolean> original) {
        return !worldguard$removable() || original.call(entity);
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void worldguard$move(MoverType type, Vec3 movement, CallbackInfo callback) {
        if (!worldguard$removable()) callback.cancel();
    }

    @Inject(method = "push(DDD)V", at = @At("HEAD"), cancellable = true)
    private void worldguard$push(double x, double y, double z, CallbackInfo callback) {
        if (!worldguard$removable()) callback.cancel();
    }
}
