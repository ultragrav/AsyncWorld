package net.ultragrav.asyncworld.customworld;

import net.ultragrav.asyncworld.AsyncChunk;
import net.ultragrav.asyncworld.AsyncWorld;
import net.ultragrav.asyncworld.ChunkLocation;

public abstract class CustomWorldAsyncChunk<T> extends AsyncChunk {


    public CustomWorldAsyncChunk(AsyncWorld parent, ChunkLocation loc) {
        super(parent, loc);
    }

    public abstract void finish(T worldServer);

    @Override
    protected synchronized void writeBlock(int section, int index, int combinedBlockId, boolean addTile) {
        this.setBlock(getLX(index), (section << 4) + getLY(index), getLZ(index), combinedBlockId, addTile);
    }

    public synchronized void writeBlock(int x, int y, int z, int combinedBlockId, boolean addTile) {
        this.setBlock(x, y, z, combinedBlockId, addTile);
    }

    public abstract void setBlock(int x, int y, int z, int combinedBlockId, boolean addTile);

    public abstract void awaitFinish();
    public abstract void fromSnap(CustomWorldChunkSnap snap);
}
