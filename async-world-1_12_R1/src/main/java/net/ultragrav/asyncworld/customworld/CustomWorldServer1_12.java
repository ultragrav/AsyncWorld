package net.ultragrav.asyncworld.customworld;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.World;

import java.util.concurrent.locks.ReentrantLock;

public class CustomWorldServer1_12 extends WorldServer {

    private static final ReentrantLock lock = new ReentrantLock();

    CustomWorldServer1_12(CustomWorldDataManager1_12 dataManager, int dimension) {
        super(
                MinecraftServer.getServer(),
                dataManager,
                dataManager.getWorldData(),
                dimension,
                MinecraftServer.getServer().methodProfiler,
                World.Environment.NORMAL,
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
    public void save(boolean forceSave, IProgressUpdate progressUpdate) throws ExceptionWorldConflict {}
}
