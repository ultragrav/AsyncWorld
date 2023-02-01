package net.ultragrav.asyncworld.customworld;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.generator.*;

import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class CustomWorldServer1_12 extends WorldServer {

    private static final ReentrantLock lock = new ReentrantLock();

    CustomWorldServer1_12(CustomWorldDataManager1_12 dataManager, int dimension, World.Environment environment) {
        super(
                MinecraftServer.getServer(),
                dataManager,
                dataManager.getWorldData(),
                dimension,
                MinecraftServer.getServer().methodProfiler,
                environment,
                new CustomWorldChunkGenerator1_12()
        );
        this.keepSpawnInMemory = false;
        lock.lock();
        try {
            this.C = new AdvancementDataWorld(null);//TODO maybe remove if causes crashes.
            this.D = new CustomFunctionData(null, MinecraftServer.getServer());
        } finally {
            lock.unlock();
        }
        this.tracker = new EntityTracker(this);
        addIWorldAccess(new WorldManager(MinecraftServer.getServer(), this));

        worldData.setDifficulty(EnumDifficulty.NORMAL);
        worldData.setSpawn(new BlockPosition(0, 61, 0));
    }

    @Override
    protected IChunkProvider n() {
        WorldServer worldserver = this;
        IChunkLoader ichunkloader = this.dataManager.createChunkLoader(this.worldProvider);
        return new ChunkProviderServer(
                this,
                ichunkloader,
                new CustomChunkGenerator(this, this.getSeed(), this.generator) {
                    @Override
                    public void recreateStructures(Chunk chunk, int i, int j) {
                    }

                    @Override
                    public Chunk getOrCreateChunk(int x, int z) {
                        Chunk chunk = new Chunk(worldserver, x, z);
                        chunk.initLighting();
                        return chunk;
                    }
                }
        );
    }

    @Override
    public void save(boolean forceSave, IProgressUpdate progressUpdate) throws ExceptionWorldConflict {
    }
}
