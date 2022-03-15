package net.ultragrav.asyncworld.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SyncScheduler {
    
    private static final Set<SyncTask> TASKS = ConcurrentHashMap.newKeySet();

    public static void sync(Runnable runnable, Plugin plugin) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        
        SyncTask task = new SyncTask(runnable);
        TASKS.add(task);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!task.isCompleted()) {
                    try {
                        task.getRunnable().run();
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                    task.setCompleted(true);
                    TASKS.remove(task);
                }
            }
        }.runTask(plugin);
    }

    public static void flush() {
        for (SyncTask task : TASKS) {
            if (task.isCompleted()) continue;

            try {
                task.getRunnable().run();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }

            task.setCompleted(true);
        }
        TASKS.clear();
    }
}
