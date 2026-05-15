package net.raderth.scalable.tick;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldTickQueues {

    private WorldTickQueues() {}

    private static final Map<ServerLevel, ChunkTickQueue> CHUNK_QUEUES = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, EntityTickQueue> ENTITY_QUEUES = new ConcurrentHashMap<>();
    private static volatile int globalMaxDeferred = 2048;

    public static ChunkTickQueue get(ServerLevel world) {
        ChunkTickQueue q = CHUNK_QUEUES.computeIfAbsent(world, w -> {
            ChunkTickQueue newQ = new ChunkTickQueue();
            newQ.setMaxDeferred(globalMaxDeferred);
            return newQ;
        });
        // ensure any new queues get the current global max
        q.setMaxDeferred(globalMaxDeferred);
        return q;
    }

    public static EntityTickQueue getEntityQueue(ServerLevel world) {
        return ENTITY_QUEUES.computeIfAbsent(world, w -> new EntityTickQueue());
    }

    public static void remove(ServerLevel world) {
        CHUNK_QUEUES.remove(world);
        ENTITY_QUEUES.remove(world);
    }

    public static void clear() {
        CHUNK_QUEUES.clear();
        ENTITY_QUEUES.clear();
    }

    public static void setMaxDeferred(int max) {
        globalMaxDeferred = max;
        for (ChunkTickQueue q : CHUNK_QUEUES.values()) {
            q.setMaxDeferred(max);
        }
    }

    public static void deferNewlyLoaded(ServerLevel world, ChunkPos pos) {
        get(world).deferNewlyLoaded(pos);
    }
}