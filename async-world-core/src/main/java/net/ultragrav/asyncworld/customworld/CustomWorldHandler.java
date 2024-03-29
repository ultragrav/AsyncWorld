package net.ultragrav.asyncworld.customworld;

import org.bukkit.World;

public interface CustomWorldHandler {
    void finishChunk(CustomWorldAsyncChunk<?> chunk);
    void createWorld(CustomWorld customWorld, String name, World.Environment environment);
    boolean isWorldCreated();
    void addToWorldList();
    World getBukkitWorld();
    void invalidateWorld();
}
