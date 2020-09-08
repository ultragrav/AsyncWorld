package net.ultragrav.asyncworld.customworld;

import net.ultragrav.asyncworld.AsyncWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomWorldChunkMap {
    private Map<Long, CustomWorldAsyncChunk<?>> chunks = new HashMap<>();

    private final CustomWorldAsyncWorld parent;

    public CustomWorldChunkMap(CustomWorldAsyncWorld parent) {
        this.parent = parent;
    }

    public synchronized List<CustomWorldAsyncChunk<?>> getCachedCopy() {
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

    public synchronized CustomWorldAsyncChunk<?> get(int cx, int cz) {
        return chunks.get(getChunkLocAsLong(cx, cz));
    }

    public synchronized CustomWorldAsyncChunk<?> getOrMake(int cx, int cz) {
        CustomWorldAsyncChunk<?> chunk = this.get(cx, cz);

        if (chunk == null) {
            chunk = parent.getNewChunk(cx, cz);
            chunks.put(getChunkLocAsLong(cx, cz), chunk);
        }
        return chunk;
    }

//    public synchronized void add(AsyncChunk chunk) {
//        if (!this.contains(chunk.getLoc().getX(), chunk.getLoc().getZ())) {
//            this.chunks.put(getChunkLocAsLong(chunk.getLoc().getX(), chunk.getLoc().getZ()), chunk);
//        }
//    }
}
