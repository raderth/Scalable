package net.raderth.scalable.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.raderth.scalable.tick.WorldTickQueues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin {

    @Shadow private int oldTicketLevel;
    @Shadow private int ticketLevel;

    @Inject(method = "updateFutures", at = @At("HEAD"))
    private void scalable$onLevelUpdate(ChunkMap chunkLoadingManager,
                                        Executor executor, CallbackInfo ci) {
        boolean wasEntityTicking = ChunkLevel.isEntityTicking(oldTicketLevel);
        boolean nowEntityTicking  = ChunkLevel.isEntityTicking(ticketLevel);

        if (!wasEntityTicking && nowEntityTicking) {
            GenerationChunkHolder self = (GenerationChunkHolder)(Object) this;
            ServerLevel world = ((ChunkMapAccessor) chunkLoadingManager).getLevel();
            WorldTickQueues.deferNewlyLoaded(world, self.getPos());
            // Mark the queue dirty so it rebuilds next tick
            WorldTickQueues.get(world).markDirty();
        }
    }
}