package net.raderth.scalable.mixin;

import net.minecraft.util.profiling.Profiler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import net.raderth.scalable.ScalableMod;
import net.raderth.scalable.tick.EntityTickQueue;
import net.raderth.scalable.tick.WorldTickQueues;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Shadow @Final private EntityTickList entityTickList;

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"
            ),
            cancellable = true
    )
    private void scalable$replaceEntityTickLoop(BooleanSupplier haveTime, CallbackInfo ci) {
        ci.cancel();

        ServerLevel self = (ServerLevel) (Object) this;
        EntityTickQueue entityQueue = WorldTickQueues.getEntityQueue(self);

        // Snapshot all currently tickable entities
        List<Entity> allEntities = new ArrayList<>();
        entityTickList.forEach(allEntities::add);

        // Rebuild with player-based assignment
        entityQueue.rebuild(self.players(), allEntities,
                self.getChunkSource().chunkMap.getDistanceManager());

        ProfilerFiller profiler = Profiler.get();
        while (!ScalableMod.BUDGET.isOverBudget()) {
            Entity entity = entityQueue.poll();
            if (entity == null) break;
            if (!entity.isRemoved()) {
                if (entity instanceof ServerPlayer
                        || self.getChunkSource().chunkMap.getDistanceManager()
                        .inEntityTickingRange(entity.chunkPosition().pack())) {
                    profiler.push("tick");
                    self.guardEntityTick(self::tickNonPassenger, entity);
                    profiler.pop();
                }
            }
        }
    }
}