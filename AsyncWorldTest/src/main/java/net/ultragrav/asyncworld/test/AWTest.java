package net.ultragrav.asyncworld.test;

import net.ultragrav.asyncworld.GlobalChunkQueue;
import net.ultragrav.asyncworld.test.cmd.CmdAsyncWorld;
import net.ultragrav.asyncworld.test.utils.EventSubscriptions;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class AWTest extends JavaPlugin {
    public static AWTest instance;
    public static WorldEditPlayerManager playerManager; // STATIC ABUSE >:)

    private long lastTick = -1;

    @Override
    public void onEnable() {
        instance = this;

        new GlobalChunkQueue(this);
        new EventSubscriptions(this);

        playerManager = new WorldEditPlayerManager(this);

        new CmdAsyncWorld().register();

        //Crash detector
        new Thread(() -> {
            try {
                while (true) {
                    long l = lastTick;
                    if (l == -2)
                        return;
                    if (l != -1 && (System.currentTimeMillis() - l) > 4000) {
                        File file = new File(getDataFolder(), "Crash-" + UUID.randomUUID().toString() + ".txt");
                        try (FileWriter writer = new FileWriter(file)) {
                            Map<?, ?> liveThreads = Thread.getAllStackTraces();
                            for (Object o : liveThreads.keySet()) {
                                Thread key = (Thread) o;
                                writer.append("Thread Name: ").append(key.getName()).append("\n");
                                writer.append("Status: ").append(key.getState().toString()).append("\n");
                                StackTraceElement[] trace = (StackTraceElement[]) liveThreads.get(key);
                                for (StackTraceElement stackTraceElement : trace) {
                                    writer.append("\tat ").append(String.valueOf(stackTraceElement)).append("\n");
                                }
                            }
                            writer.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return;
                    }
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () ->
                lastTick = System.currentTimeMillis(), 1, 1);
    }
}
