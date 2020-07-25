package net.ultragrav.asyncworld.plugin;

import net.ultragrav.asyncworld.GlobalChunkQueue;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginAsyncWorld extends JavaPlugin {
    public static PluginAsyncWorld instance;

    @Override
    public void onEnable() {
        instance = this;
        new GlobalChunkQueue(this);
    }
}
