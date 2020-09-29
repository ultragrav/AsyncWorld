package net.ultragrav.asyncworld.customworld;

import net.minecraft.server.v1_12_R1.Chunk;
import net.minecraft.server.v1_12_R1.ChunkRegionLoader;
import net.minecraft.server.v1_12_R1.World;

public class CustomWorldChunkLoader1_12 extends ChunkRegionLoader {
    private final CustomWorld customWorld;

    public CustomWorldChunkLoader1_12(CustomWorld world) {
        super(null, null);
        this.customWorld = world;
    }

    // Load chunk
    @Override
    public Chunk a(World nmsWorld, int x, int z) {
        CustomWorldAsyncChunk1_12 c = ((CustomWorldAsyncChunk1_12)customWorld.getChunk(x, z));
        if(c == null) {
            return null;
        }
        Chunk chunk = c.getStoredChunk();
        chunk.d(true);
        chunk.e(true);
        return chunk;
    }

    @Override
    public void saveChunk(World world, Chunk chunk, boolean unloaded) {
    }

    // Save all chunks
    @Override
    public void c() {
        // All chunks are cached in CustomWorld, so this is not necessary
    }

    @Override
    public boolean chunkExists(int x, int z) {
        return true; // Prevent chunk generator from being called
    }

    // Does literally nothing
    @Override
    public void b() {
    }
}
