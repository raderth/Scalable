package net.raderth.scalable.tick;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

public final class EntityTickQueue {

    private final Map<UUID, ArrayDeque<Entity>> playerQueues = new LinkedHashMap<>();
    private final ArrayDeque<Entity> priorityEntities = new ArrayDeque<>();
    private int cursor = 0;

    public void rebuild(List<ServerPlayer> players,
                        Iterable<Entity> allEntities,
                        DistanceManager distanceManager) {
        playerQueues.clear();
        priorityEntities.clear();

        Map<ChunkPos, ServerPlayer> chunkOwner = new HashMap<>(); // unused directly, assignment below

        for (ServerPlayer p : players) {
            playerQueues.put(p.getUUID(), new ArrayDeque<>());
        }

        for (Entity entity : allEntities) {
            if (entity.isRemoved()) continue;
            if (entity instanceof ServerPlayer) {
                priorityEntities.add(entity);
                continue;
            }
            ChunkPos eChunk = entity.chunkPosition();
            // Find nearest player
            ServerPlayer nearest = null;
            double bestDist = Double.MAX_VALUE;
            for (ServerPlayer p : players) {
                double dx = p.chunkPosition().x() - eChunk.x();
                double dz = p.chunkPosition().z() - eChunk.z();
                double d = dx * dx + dz * dz;
                if (d < bestDist) {
                    bestDist = d;
                    nearest = p;
                }
            }
            if (nearest != null) {
                playerQueues.get(nearest.getUUID()).add(entity);
            } else {
                priorityEntities.add(entity);
            }
        }

        if (!playerQueues.isEmpty()) cursor = cursor % playerQueues.size();
    }

    public Entity poll() {
        if (!priorityEntities.isEmpty()) return priorityEntities.poll();
        return pollRoundRobin();
    }

    private Entity pollRoundRobin() {
        List<ArrayDeque<Entity>> queues = new ArrayList<>(playerQueues.values());
        int size = queues.size();
        if (size == 0) return null;
        for (int i = 0; i < size; i++) {
            ArrayDeque<Entity> q = queues.get(cursor % size);
            cursor = (cursor + 1) % size;
            if (!q.isEmpty()) return q.poll();
        }
        return null;
    }

    public boolean isEmpty() {
        if (!priorityEntities.isEmpty()) return false;
        return playerQueues.values().stream().allMatch(ArrayDeque::isEmpty);
    }
}