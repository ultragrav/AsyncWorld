package net.ultragrav.asyncworld;

import org.bukkit.plugin.Plugin;

public class GlobalChunkQueue extends ChunkQueue {
    public static GlobalChunkQueue instance;

    public GlobalChunkQueue(Plugin plugin) {
        super(plugin);
        if (instance != null) {
            instance.setWorking(false);
        }
        instance = this;
    }
}
