package net.ultragrav.asyncworld.customworld;

import net.ultragrav.asyncworld.AsyncWorld;
import net.ultragrav.serializer.GravSerializable;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public abstract class CustomWorld {
    public abstract String getName();
    public abstract World getBukkitWorld();
    public abstract boolean isWorldCreated();
    public abstract World.Environment getEnvironment();
    public abstract void create(Consumer<CustomWorldAsyncWorld> generator);

    public void create(SavedCustomWorld world) { this.create(world, true); }
    public abstract void create(SavedCustomWorld world, boolean preloadChunks);
    public abstract CustomWorldAsyncChunk<?> getChunk(int cx, int cz);
    public abstract void unloadAndSaveChunk(int cx, int cz);

    public abstract Plugin getPlugin();
    public abstract void unload();
    public abstract SavedCustomWorld getSavedCustomWorld();
    public abstract SavedCustomWorld getSavedCustomWorld(CustomWorldAsyncWorld world);

    public abstract CustomWorldAsyncWorld getAsyncWorld();
}
