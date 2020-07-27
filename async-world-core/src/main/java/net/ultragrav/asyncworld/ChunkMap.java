package net.ultragrav.asyncworld;

import java.util.ArrayList;
import java.util.List;

public class ChunkMap {
    private List<AsyncChunk> chunks = new ArrayList<>();

    private AsyncWorld parent;

    public ChunkMap(AsyncWorld parent) {
        this.parent = parent;
    }

    public synchronized List<AsyncChunk> getCachedCopy() {
        return new ArrayList<>(chunks);
    }

    public boolean contains(ChunkLocation location) {
        for (AsyncChunk chunk : chunks) {
            if (chunk.getLoc().equals(location))
                return true;
        }
        return false;
    }

    public synchronized void clear() {
        this.chunks.clear();
    }

    public synchronized AsyncChunk get(int cx, int cz) {
        for (AsyncChunk chunk : chunks) {
            if (chunk.getLoc().getX() == cx && chunk.getLoc().getZ() == cz)
                return chunk;
        }
        return null;
    }

    public synchronized AsyncChunk getOrMake(int cx, int cz) {
        AsyncChunk chunk = this.get(cx, cz);

        if (chunk == null) {
            chunk = parent.getNewChunk(cx, cz);
            chunks.add(chunk);
        }
        return chunk;
    }

    public synchronized void add(AsyncChunk chunk) {
        if (!this.contains(chunk.getLoc()))
            this.chunks.add(chunk);
    }
}
