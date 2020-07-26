package main.java.net.ultragrav.asyncworld;

import net.ultragrav.asyncworld.plugin.PluginAsyncWorld;

public class GlobalChunkQueue extends ChunkQueue {
    public static GlobalChunkQueue instance;

    public GlobalChunkQueue(PluginAsyncWorld plugin) {
        super(plugin);
        instance = this;
    }
}
