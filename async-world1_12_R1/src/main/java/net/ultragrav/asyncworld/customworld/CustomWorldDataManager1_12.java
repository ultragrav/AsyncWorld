package net.ultragrav.asyncworld.customworld;

import lombok.AccessLevel;
import lombok.Getter;
import net.minecraft.server.v1_12_R1.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.UUID;

public class CustomWorldDataManager1_12 extends WorldNBTStorage {
    @Getter(value = AccessLevel.NONE)
    private final UUID uuid = UUID.randomUUID();
    private final CustomWorld world;
    private final CustomWorldChunkLoader1_12 chunkLoader;
    private WorldData worldData;

    // When unloading a world, Spigot tries to remove the region file from its cache.
    // To do so, it casts the world's IDataManager to a WorldNBTStorage, to be able
    // to use the getDirectory() method. Thanks to this, we have to create a custom
    // WorldNBTStorage with a fake file instead of just implementing the IDataManager interface
    //
    // Thanks Spigot!
    CustomWorldDataManager1_12(CustomWorld world) {
        super(
                new File("temp"),
                "temp",
                false,
                null
        );

        this.world = world;
        this.chunkLoader = new CustomWorldChunkLoader1_12(world);
    }

    @NotNull
    @Override
    public WorldData getWorldData() {
        if (worldData == null) {
            worldData = new WorldData(new WorldSettings(
                    0,
                    EnumGamemode.SURVIVAL,
                    false, // Map features
                    false, // Hardcore
                    WorldType.CUSTOMIZED
            ), world.getName());
        }
        return worldData;
    }

    @Override
    public void checkSession() {
    }

    @Override
    public IChunkLoader createChunkLoader(WorldProvider worldProvider) {
        return chunkLoader;
    }

    @Override
    public void saveWorldData(WorldData worldData, NBTTagCompound nbtTagCompound) {

    }

    @Override
    public void saveWorldData(WorldData worldData) {
        this.saveWorldData(worldData, null);
    }

    @Override
    public void a() {

    }

    @Override
    public File getDataFile(String s) {
        return null;
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public void save(EntityHuman entityHuman) {

    }

    @Override
    public NBTTagCompound load(EntityHuman entityHuman) {
        return null;
    }

    @Override
    public String[] getSeenPlayers() {
        return new String[0];
    }
}
