package main.java.net.ultragrav.asyncworld;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChunkQueue implements Listener {
    public static int WORK_TIME_PER_TICK_MS = 15;
    public static int THREADS = Runtime.getRuntime().availableProcessors();
    ExecutorService executor = Executors.newCachedThreadPool();
    boolean useGC = false;
    private Plugin plugin;
    private Map<Integer, CompletableFuture<Void>> callbacks = new HashMap<>();
    private List<AsyncChunk> queue = new ArrayList<>();
    private int taskId = -1;
    private long lastGC = System.currentTimeMillis();
    private boolean working;

    public ChunkQueue(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Update chunks
     *
     * @return List of AsyncChunks updated and to be removed from the queue
     */
    protected List<AsyncChunk> update(List<AsyncChunk> chunks, long time) {
        if (chunks.size() == 0)
            return new ArrayList<>();
        long ms = System.currentTimeMillis();

        if (chunks.size() > 200) {
            useGC = true;
        }

        List<AsyncChunk> toRemove = new ArrayList<>();
        Iterator<AsyncChunk> it = chunks.iterator();
        if (THREADS == 1) {
            while (it.hasNext() && System.currentTimeMillis() - ms < time) {
                AsyncChunk chunk = it.next();
                //call will just return if the chunk was already flushed (and not edited afterwards) by another update of call
                int mask = chunk.getEditedSections();
                chunk.start();
                chunk.call(); // Using call as a wrapper to update to make it synchronized so no read/writes to that chunk while it is being flushed
                chunk.end(mask);
                toRemove.add(chunk);
            }
        } else {
            try {
                List<CompletableFuture<AsyncChunk>> futures = new ArrayList<>();
                while (it.hasNext() && System.currentTimeMillis() - ms < time) {
                    Map<AsyncChunk, Integer> masks = new ConcurrentHashMap<>();
                    for (int i = 0; i < THREADS && it.hasNext() && System.currentTimeMillis() - ms < time; i++) { // Spawn THREAD threads
                        AsyncChunk chunk = it.next();
                        chunk.start();

                        CompletableFuture<AsyncChunk> future = new CompletableFuture<>();
                        executor.execute(() -> {
                            synchronized (chunk) {
                                masks.put(chunk, chunk.getEditedSections());
                                chunk.call();
                            }
                            future.complete(chunk);
                        });
                        futures.add(future);
                    }
                    Iterator<CompletableFuture<AsyncChunk>> futureIterator = futures.iterator();
                    CompletableFuture<AsyncChunk> future;
                    while (futureIterator.hasNext()) {
                        future = futureIterator.next();
                        AsyncChunk chunk = future.get();
                        toRemove.add(chunk);
                        chunk.end(masks.get(chunk));
                        futureIterator.remove();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        chunks.removeAll(toRemove);
        if (chunks.isEmpty()) {
            //Got rid of cancelTask so this runs every tick
            //That way if a chunk is scheduled it could be updated on the same tick instead of 1 tick later

            //GC
            if (Runtime.getRuntime().freeMemory() / (double) Runtime.getRuntime().totalMemory() > 0.9
                    || System.currentTimeMillis() - lastGC > 20000 || useGC) {
                System.gc();
                useGC = false;
                lastGC = System.currentTimeMillis();
            }
        }
        return toRemove;
    }

    @EventHandler
    private void onDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin))
            cancelTask();
    }

    public synchronized void cancelTask() {
        if (taskId == -1) {
            return;
        }
        Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
    }

    public synchronized void startWork() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            synchronized (this) {
                int size = getQueueSize();
                removeAll(update(getQueueCopy(), WORK_TIME_PER_TICK_MS));
                int newSize = getQueueSize();
                for (int i = size; i >= newSize; i--) {
                    CompletableFuture<Void> future = callbacks.remove(i);
                    if (future != null)
                        future.complete(null);
                }
                if (newSize == 0)
                    callbacks.clear(); // Just in case
            }
        }, 1, 1);
    }

    public synchronized int getQueueSize() {
        return this.queue.size();
    }

    public synchronized void removeAll(List<AsyncChunk> chunks) {
        this.queue.removeAll(chunks);
    }

    public CompletableFuture<Void> queueChunk(AsyncChunk chunk) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.callbacks.put(queue.size(), future);
        this.queue.add(chunk);
        if (!this.isWorking()) {
            this.setWorking(true);
            this.startWork();
        }
        return future;
    }

    public synchronized CompletableFuture<Void> queueChunks(List<AsyncChunk> chunks) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.callbacks.put(queue.size(), future);
        this.queue.addAll(chunks);
        if (!this.isWorking()) {
            this.setWorking(true);
            this.startWork();
        }
        return future;
    }

    private synchronized boolean isWorking() {
        return this.working;
    }

    private synchronized void setWorking(boolean value) {
        this.working = value;
    }

    public synchronized List<AsyncChunk> getQueueCopy() {
        return new ArrayList<>(queue);
    }
}
