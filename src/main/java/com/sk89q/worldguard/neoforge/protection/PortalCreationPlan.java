package com.sk89q.worldguard.neoforge.protection;

import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

/** Plan vanilla's portal writes without touching blocks; commit after checking the entire frame. */
public final class PortalCreationPlan implements AutoCloseable {
    private record Write(BlockPos position, BlockState state, int flags) { }
    private static final ThreadLocal<PortalCreationPlan> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Entity> ACTOR = new ThreadLocal<>();
    private final PortalCreationPlan previous = ACTIVE.get();
    private final ServerLevel level;
    private final Entity actor = ACTOR.get();
    private final List<Write> writes = new ArrayList<>();
    public PortalCreationPlan(ServerLevel level) { this.level=level; ACTIVE.set(this); }
    public static final class ActorScope implements AutoCloseable {
        private final Entity previous = ACTOR.get();
        public ActorScope(Entity actor) { if (actor == null) ACTOR.remove(); else ACTOR.set(actor); }
        @Override public void close() { if (previous == null) ACTOR.remove(); else ACTOR.set(previous); }
    }
    public static boolean record(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        PortalCreationPlan plan = ACTIVE.get();
        if (plan == null || plan.level != level) return false;
        plan.writes.add(new Write(pos.immutable(),state,flags)); return true;
    }
    public boolean allowed() {
        var service = WorldGuardMod.service();
        if (service == null) return true;
        if (!service.healthy()) return false;
        if (!service.config().bool(level.dimension().location().toString(),"regions.nether-portal-protection",true)) return true;
        for (Write write : writes) {
            if (!service.canUse(level,write.position,actor,"block-break") || !service.canUse(level,write.position,actor,"block-place")) return false;
        }
        return true;
    }
    public void commit() {
        // Closing the plan before committing lets the original Level hooks run normally.
        close();
        for (Write write : writes) level.setBlock(write.position,write.state,write.flags);
    }
    @Override public void close() { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
}
