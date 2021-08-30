package net.ultragrav.asyncworld.customworld;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public class CustomWorldServer1_12 extends WorldServer {
    CustomWorldServer1_12(@NotNull CustomWorldDataManager1_12 dataManager, int dimension) {
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
        this.C = new AdvancementDataWorld(null);//TODO maybe remove if causes crashes.
        this.D = new CustomFunctionData(null, MinecraftServer.getServer());
        this.tracker = new EntityTracker(this);
        addIWorldAccess(new WorldManager(MinecraftServer.getServer(), this));

        worldData.setDifficulty(EnumDifficulty.NORMAL);
        worldData.setSpawn(new BlockPosition(0, 61, 0));
    }

    @Override
    public void save(boolean forceSave, IProgressUpdate progressUpdate) throws ExceptionWorldConflict {
        super.save(forceSave, progressUpdate);
    }

    private void save() {}
}
