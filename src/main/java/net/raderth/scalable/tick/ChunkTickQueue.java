package net.raderth.scalable.tick;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.raderth.scalable.mixin.ChunkMapAccessor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkTickQueue {

    private static final Set<TicketType> PRIORITY_TYPES = Set.of(
            TicketType.PORTAL,
            TicketType.ENDER_PEARL,
            TicketType.FORCED,
            TicketType.UNKNOWN,
            TicketType.DRAGON
    );

    private static final int VELOCITY_LOOKAHEAD_TICKS = 20;

    private final ArrayDeque<ChunkPos> deferred = new ArrayDeque<>();
    private final Set<ChunkPos> deferredSet = new HashSet<>();
    private final ArrayDeque<ChunkPos> priority = new ArrayDeque<>();
    private final List<ArrayDeque<ChunkPos>> playerQueues = new ArrayList<>();
    private int cursor = 0;

    // --- Smart incremental rebuild ---
    private boolean dirty = true;
    private final Map<UUID, ChunkPos> lastPlayerPositions = new HashMap<>();
    private int lastHolderCount = 0;

    // --- Dynamic max deferred ---
    private int maxDeferred = 2048;

    public void setMaxDeferred(int max) {
        this.maxDeferred = max;
        // Prune excess entries if new limit is smaller
        while (deferred.size() > maxDeferred) {
            ChunkPos evicted = deferred.poll();
            deferredSet.remove(evicted);
        }
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void rebuildIfNeeded(ChunkMap chunkLoadingManager, List<ServerPlayer> players) {
        boolean playerMoved = false;
        for (ServerPlayer p : players) {
            ChunkPos current = p.chunkPosition();
            if (!current.equals(lastPlayerPositions.get(p.getUUID()))) {
                playerMoved = true;
                lastPlayerPositions.put(p.getUUID(), current);
            }
        }
        // Remove stale players
        lastPlayerPositions.keySet()
                .removeIf(id -> players.stream().noneMatch(p -> p.getUUID().equals(id)));

        ChunkMapAccessor accessor = (ChunkMapAccessor) chunkLoadingManager;
        int holderCount = accessor.getVisibleChunkMap().size();
        if (holderCount != lastHolderCount) {
            dirty = true;
            lastHolderCount = holderCount;
        }

        if (!dirty && !playerMoved) return;

        rebuild(chunkLoadingManager, players);
        dirty = false;
    }

    // --- Rebuild (now uses velocity projection) ---
    public void rebuild(ChunkMap chunkLoadingManager, List<ServerPlayer> players) {
        priority.clear();
        playerQueues.clear();

        ChunkMapAccessor accessor = (ChunkMapAccessor) chunkLoadingManager;

        Set<ChunkPos> allTickingPos = new LinkedHashSet<>();
        Set<ChunkPos> priorityPos = new LinkedHashSet<>();

        for (ChunkHolder holder : accessor.getVisibleChunkMap().values()) {
            LevelChunk wc = holder.getTickingChunk();
            if (wc == null) continue;

            ChunkPos pos = holder.getPos();
            allTickingPos.add(pos);

            for (Ticket ticket : accessor.getTicketStorage().getTickets(ChunkPos.pack(pos.x(), pos.z()))) {
                if (PRIORITY_TYPES.contains(ticket.getType())) {
                    priorityPos.add(pos);
                    break;
                }
            }
        }

        priority.addAll(priorityPos);

        // Seed assigned with priority and deferred chunks
        Set<ChunkPos> assigned = new HashSet<>(priorityPos);
        assigned.addAll(deferredSet);

        List<ServerPlayer> sorted = new ArrayList<>(players);
        sorted.sort(Comparator.comparing(p -> p.getUUID().toString()));

        for (ServerPlayer player : sorted) {
            Vec3 vel = player.getDeltaMovement();
            double projX = (player.getX() + vel.x * VELOCITY_LOOKAHEAD_TICKS) / 16.0;
            double projZ = (player.getZ() + vel.z * VELOCITY_LOOKAHEAD_TICKS) / 16.0;

            List<ChunkPos> playerChunks = new ArrayList<>();
            for (ChunkPos pos : allTickingPos) {
                if (!assigned.contains(pos)) {
                    playerChunks.add(pos);
                }
            }

            playerChunks.sort(Comparator.comparingDouble(pos -> {
                double dx = pos.x() - projX;
                double dz = pos.z() - projZ;
                return dx * dx + dz * dz;
            }));

            ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
            for (ChunkPos pos : playerChunks) {
                if (assigned.add(pos)) {
                    queue.add(pos);
                }
            }

            if (!queue.isEmpty()) {
                playerQueues.add(queue);
            }
        }

        // Unclaimed chunks go to priority
        for (ChunkPos pos : allTickingPos) {
            if (!assigned.contains(pos)) {
                priority.addLast(pos);
            }
        }

        if (!playerQueues.isEmpty()) {
            cursor = cursor % playerQueues.size();
        } else {
            cursor = 0;
        }
    }

    // --- Polling ---
    public ChunkPos poll() {
        if (!deferred.isEmpty()) {
            ChunkPos pos = deferred.poll();
            deferredSet.remove(pos);
            return pos;
        }
        if (!priority.isEmpty()) return priority.poll();
        return pollPlayerRoundRobin();
    }

    private ChunkPos pollPlayerRoundRobin() {
        int size = playerQueues.size();
        if (size == 0) return null;
        for (int attempt = 0; attempt < size; attempt++) {
            int idx = cursor % size;
            cursor = (cursor + 1) % size;
            ArrayDeque<ChunkPos> q = playerQueues.get(idx);
            if (!q.isEmpty()) return q.poll();
        }
        return null;
    }

    public boolean isEmpty() {
        if (!deferred.isEmpty() || !priority.isEmpty()) return false;
        return playerQueues.stream().allMatch(ArrayDeque::isEmpty);
    }

    public void deferRemaining() {
        while (!priority.isEmpty()) {
            addToDeferred(priority.poll());
        }
        for (ArrayDeque<ChunkPos> q : playerQueues) {
            while (!q.isEmpty()) {
                addToDeferred(q.poll());
            }
        }
    }

    private void addToDeferred(ChunkPos pos) {
        if (deferredSet.contains(pos)) return;
        if (deferred.size() >= maxDeferred) {
            ChunkPos evicted = deferred.poll();
            deferredSet.remove(evicted);
        }
        deferred.addLast(pos);
        deferredSet.add(pos);
    }

    public void deferNewlyLoaded(ChunkPos pos) {
        addToDeferred(pos);
    }

    public int getDeferredSize() { return deferred.size(); }

    public int getRemainingSize() {
        int count = deferred.size() + priority.size();
        for (ArrayDeque<ChunkPos> q : playerQueues) count += q.size();
        return count;
    }

    public static boolean isPriorityType(TicketType type) {
        return PRIORITY_TYPES.contains(type);
    }
}