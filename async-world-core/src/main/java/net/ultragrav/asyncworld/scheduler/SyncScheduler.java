package net.ultragrav.asyncworld.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class SyncScheduler {

    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final Set<SyncTask> TASKS = new HashSet<>();

    public static void sync(Runnable runnable, Plugin plugin) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }

        SyncTask task = new SyncTask(runnable);
        LOCK.lock();
        TASKS.add(task);
        LOCK.unlock();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (task.tryComplete()) {

                    try {
                        task.getRunnable().run();
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }

                    LOCK.lock();
                    TASKS.remove(task);
                    LOCK.unlock();

                }
            }
        }.runTask(plugin);
    }

    public static void flush() {

        LOCK.lock();
        Set<SyncTask> copiedTasks = new HashSet<>(TASKS);
        TASKS.clear();
        LOCK.unlock();

        for (SyncTask task : copiedTasks) {
            if (!task.tryComplete()) continue;

            try {
                task.getRunnable().run();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }

        }
    }
}
