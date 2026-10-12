package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionEvents;
import com.sk89q.worldguard.neoforge.rules.WorldRulesHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TurtleEggBlock.class)
public abstract class TramplingMixin {
    @Inject(method = "destroyEgg", at = @At("HEAD"), cancellable = true)
    private void worldguard$trample(Level level, BlockState state, BlockPos pos, Entity entity, int chance, CallbackInfo callback) {
        if (level instanceof ServerLevel server && (WorldRulesHooks.bool(server, entity instanceof net.minecraft.world.entity.player.Player
                ? "turtle-egg.disable-player-trampling" : "turtle-egg.disable-creature-trampling")
                || !ProtectionEvents.use(server, pos, ProtectionEvents.responsible(entity), "block-trampling"))) callback.cancel();
    }
}
