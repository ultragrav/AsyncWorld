package net.ultragrav.asyncworld.customworld;

import net.ultragrav.asyncworld.AsyncWorld;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public abstract class CustomWorld {
    public abstract String getName();
    public abstract World getBukkitWorld();
    public abstract void create(Consumer<AsyncWorld> generator);
    public abstract CustomWorldAsyncChunk<?> getChunk(int cx, int cz);

    public abstract Plugin getPlugin();
    public abstract void unload();
}
