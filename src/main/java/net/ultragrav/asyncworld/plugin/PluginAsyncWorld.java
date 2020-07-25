package net.ultragrav.asyncworld.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public class PluginAsyncWorld extends JavaPlugin {
    public static PluginAsyncWorld instance;

    @Override
    public void onEnable() {
        instance = this;
    }
}
