package net.raderth.scalable.mixin;

import net.minecraft.util.profiling.Profiler;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.util.profiling.ProfilerFiller;
import net.raderth.scalable.ScalableMod;
import net.raderth.scalable.tick.ChunkTickQueue;
import net.raderth.scalable.tick.WorldTickQueues;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {

    @Shadow @Final ServerLevel level;
    @Shadow public ChunkMap chunkMap;
    @Shadow private long lastInhabitedUpdate;   // used in tickChunks()

    @Shadow
    protected abstract void broadcastChangedChunks(ProfilerFiller profiler);  // still needed

    private static final int LOG_INTERVAL_TICKS = 200;
    private int tickCounter = 0;

    /**
     * Override the vanilla tickChunks() to use budget-aware block ticking.
     * We keep the lastInhabitedUpdate logic and profiler pushes.
     */
    @Inject(method = "tickChunks", at = @At("HEAD"), cancellable = true)
    private void scalable$replaceTickChunks(CallbackInfo ci) {
        ci.cancel();

        long time = this.level.getGameTime();
        long timeDiff = time - this.lastInhabitedUpdate;
        this.lastInhabitedUpdate = time;

        if (!this.level.isDebug()) {
            ProfilerFiller profiler = Profiler.get();
            profiler.push("pollingChunks");
            if (this.level.tickRateManager().runsNormally()) {
                profiler.push("tickingChunks");

                ChunkTickQueue queue = WorldTickQueues.get(level);
                int randomTickSpeed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
                int deferredAtStart = queue.getDeferredSize();
                queue.rebuildIfNeeded(chunkMap, level.players());

                int ticked = 0, skippedNull = 0, deferredConsumed = 0;
                while (!ScalableMod.BUDGET.isOverBudget()) {
                    ChunkPos pos = queue.poll();
                    if (pos == null) break;
                    boolean fromDeferred = (ticked + skippedNull) < deferredAtStart;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
                    if (chunk == null) {
                        skippedNull++;
                        if (fromDeferred) {
                            ScalableMod.LOGGER.warn(
                                    "[Scalable] Deferred chunk [{},{}] in world '{}' was null on poll — " +
                                            "it unloaded before it could be processed. Entry dropped.",
                                    pos.x(), pos.z(), level.dimension().identifier());
                        }
                        continue;
                    }
                    if (fromDeferred) deferredConsumed++;
                    ticked++;
                    level.tickChunk(chunk, randomTickSpeed);
                }

                int remainingBeforeDefer = queue.getRemainingSize();
                queue.deferRemaining();
                int deferredAtEnd = queue.getDeferredSize();

                String worldName = level.dimension().identifier().toString();
                if (remainingBeforeDefer > 0) {
                    ScalableMod.LOGGER.warn(
                            "[Scalable] OVER BUDGET '{}': ticked={} deferred_now={} " +
                                    "carry_over_consumed={}/{} null_skipped={} queue_total={}",
                            worldName, ticked, remainingBeforeDefer,
                            deferredConsumed, deferredAtStart, skippedNull, deferredAtEnd);
                }

                tickCounter++;

                profiler.pop();
            }
            this.broadcastChangedChunks(profiler);
            profiler.pop();
        }
    }
}