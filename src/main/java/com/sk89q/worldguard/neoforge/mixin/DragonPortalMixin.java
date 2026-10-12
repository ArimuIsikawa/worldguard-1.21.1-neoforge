package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.rules.WorldRulesHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndDragonFight.class)
public abstract class DragonPortalMixin {
    @Shadow @Final private ServerLevel level;
    @Inject(method = "spawnExitPortal", at = @At("HEAD"), cancellable = true)
    private void worldguard$portal(boolean active, CallbackInfo callback) {
        if (WorldRulesHooks.bool(level, "mobs.block-enderdragon-portal-creation")) callback.cancel();
    }
}
