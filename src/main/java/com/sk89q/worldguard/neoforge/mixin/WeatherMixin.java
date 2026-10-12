package com.sk89q.worldguard.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerLevel.class)
public abstract class WeatherMixin {
    @WrapMethod(method = "tickChunk")
    private void worldguard$weather(LevelChunk chunk, int randomTicks, Operation<Void> original) {
        try (var scope = ProtectionHooks.scope("weather", null)) { original.call(chunk, randomTicks); }
    }
}
