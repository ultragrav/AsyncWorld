package net.ultragrav.asyncworld;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChunkMap {
    private Map<Long, AsyncChunk> chunks = new HashMap<>();

    private AsyncWorld parent;

    public ChunkMap(AsyncWorld parent) {
        this.parent = parent;
    }

    public synchronized List<AsyncChunk> getCachedCopy() {
        return new ArrayList<>(chunks.values());
    }

    public synchronized boolean contains(int cx, int cz) {
        return chunks.containsKey(getChunkLocAsLong(cx, cz));
    }

    public synchronized void clear() {
        this.chunks.clear();
    }

    private long getChunkLocAsLong(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    public synchronized AsyncChunk get(int cx, int cz) {
        return chunks.get(getChunkLocAsLong(cx, cz));
    }

    public synchronized AsyncChunk getOrMake(int cx, int cz) {
        AsyncChunk chunk = this.get(cx, cz);

        if (chunk == null) {
            chunk = parent.getNewChunk(cx, cz);
            add(chunk);
        }
        return chunk;
    }

    public synchronized void add(AsyncChunk chunk) {
        if (!this.contains(chunk.getLoc().getX(), chunk.getLoc().getZ())) {
            this.chunks.put(getChunkLocAsLong(chunk.getLoc().getX(), chunk.getLoc().getZ()), chunk);
        }
    }
}
