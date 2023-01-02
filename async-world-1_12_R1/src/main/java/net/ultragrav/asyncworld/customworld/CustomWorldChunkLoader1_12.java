package net.ultragrav.asyncworld.customworld;

import net.minecraft.server.v1_12_R1.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CustomWorldChunkLoader1_12 implements IChunkLoader {
    private final CustomWorld customWorld;

    public CustomWorldChunkLoader1_12(CustomWorld world) {
        this.customWorld = world;
    }

    // Load chunk
    @Override
    public Chunk a(World nmsWorld, int x, int z) {
        CustomWorldAsyncChunk1_12 c = ((CustomWorldAsyncChunk1_12) customWorld.getChunk(x, z));
        if (c == null) {
            return null;
        }
        Chunk chunk = c.getStoredChunk();
        chunk.d(true);
        chunk.e(true);

        if(x != chunk.locX || z != chunk.locZ)
            throw new IllegalStateException("Chunk loaded was not of required location: " + x + " " + z + ", it was " + chunk.locX + " " + chunk.locZ);

        for (List<Entity> entitySlice : chunk.getEntitySlices()) {
            if (entitySlice == null) continue;
            //Remove all player entities. This will... never happen except for when using NPCs.
            for (int i = 0; i < entitySlice.size(); i++) {
                Entity e = entitySlice.get(i);
                if (e instanceof EntityPlayer) {
                    entitySlice.remove(i);
                    i--;
                }
            }
        }

        return chunk;
    }

    @Override
    public void saveChunk(World world, Chunk chunk, boolean unloaded) {
        if (unloaded) customWorld.unloadAndSaveChunk(chunk.locX, chunk.locZ);
    }

    // Save all chunks
    @Override
    public void c() {
        // All chunks are cached in CustomWorld, so this is not necessary
    }

    @Override
    public boolean chunkExists(int x, int z) {
        CustomWorldAsyncChunk1_12 c = ((CustomWorldAsyncChunk1_12) customWorld.getChunk(x, z));
        return c != null;
    }

    // Does literally nothing
    @Override
    public void b() {
    }
}
