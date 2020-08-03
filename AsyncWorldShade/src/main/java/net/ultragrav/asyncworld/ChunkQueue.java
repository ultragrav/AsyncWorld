package net.ultragrav.asyncworld;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class ChunkQueue implements Listener {
    public static int WORK_TIME_PER_TICK_MS = 15;
    public static int THREADS = Runtime.getRuntime().availableProcessors();
    ExecutorService executor = Executors.newCachedThreadPool();
    boolean useGC = false;
    private final Plugin plugin;
    private final List<QueuedChunk> queue = new ArrayList<>();
    private int taskId = -1;
    private long lastGC = System.currentTimeMillis();
    private boolean working;
    private final ReentrantLock listLock = new ReentrantLock(true);

    public ChunkQueue(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Update chunks
     *
     * @return List of AsyncChunks updated and to be removed from the queue
     */
    protected List<CompletableFuture<Void>> update(List<QueuedChunk> chunks, ReentrantLock listLock, long time) {
        if (chunks.size() == 0)
            return new ArrayList<>();
        long ms = System.currentTimeMillis();

        if (chunks.size() > 200) {
            useGC = true;
        }

        List<CompletableFuture<Void>> callbacks = new ArrayList<>();

        if (THREADS == 1) {
            while (System.currentTimeMillis() - ms < time) {
                listLock.lock();
                AsyncChunk chunk;
                try {
                    if(chunks.size() > 0) {
                        chunk = chunks.get(0).getChunk();
                        callbacks.add(chunks.get(0).getCallback());
                        chunks.remove(0);
                    } else {
                        break;
                    }
                } finally {
                    listLock.unlock();
                }
                //call will just return if the chunk was already flushed (and not edited afterwards) by another update of call
                int mask = chunk.getEditedSections();
                chunk.start();
                chunk.call(); // Using call as a wrapper to update to make it synchronized so no read/writes to that chunk while it is being flushed
                chunk.end(mask);
            }
        } else {
            try {
                List<CompletableFuture<AsyncChunk>> futures = new ArrayList<>();
                while (System.currentTimeMillis() - ms < time) {
                    Map<AsyncChunk, Integer> masks = new ConcurrentHashMap<>();

                    //Lock it so that while we schedule the tasks, the list isn't changed
                    listLock.lock();
                    try {
                        for (int i = 0; i < THREADS && chunks.size() > 0 && System.currentTimeMillis() - ms < time; i++) { // Spawn THREAD threads
                            AsyncChunk chunk = chunks.get(0).getChunk();
                            callbacks.add(chunks.get(0).getCallback());
                            chunks.remove(0);
                            chunk.start();

                            CompletableFuture<AsyncChunk> future = new CompletableFuture<>();

                            //NOTE this is a just the scheduling of the task, not the execution, so this doesn't take long
                            executor.execute(() -> {
                                synchronized (chunk) {
                                    masks.put(chunk, chunk.getEditedSections());
                                    chunk.call();
                                }
                                future.complete(chunk);
                            });
                            futures.add(future);
                        }
                    } finally {
                        listLock.unlock(); //Unlock
                    }
                    Iterator<CompletableFuture<AsyncChunk>> futureIterator = futures.iterator();
                    CompletableFuture<AsyncChunk> future;
                    while (futureIterator.hasNext()) {
                        future = futureIterator.next();
                        AsyncChunk chunk = future.get();
                        chunk.end(masks.get(chunk));
                        futureIterator.remove();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (chunks.isEmpty()) {
            Bukkit.broadcastMessage("Finished queue");
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
        return callbacks;
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
                List<CompletableFuture<Void>> callbacks = update(queue, listLock, WORK_TIME_PER_TICK_MS);
                callbacks.forEach(c -> {
                    if(c == null)
                        return;
                    c.complete(null);
                });
            }
        }, 1, 1);
    }

    public synchronized int getQueueSize() {
        return this.queue.size();
    }

    public CompletableFuture<Void> queueChunk(AsyncChunk chunk) {
        return queueChunk(chunk, new CompletableFuture<>());
    }

    public CompletableFuture<Void> queueChunk(AsyncChunk chunk, CompletableFuture<Void> future) {
        listLock.lock();
        try {
            QueuedChunk queuedChunk = new QueuedChunk(chunk, future);
            //Check if we should merge
            for (QueuedChunk queuedChunk1 : queue) {
                if (queuedChunk1.getChunk().getLoc().equals(chunk.getLoc())) {

                    //Merge Chunks
                    queuedChunk1.getChunk().merge(chunk);

                    //Merge callbacks
                    queuedChunk1.setCallback(queuedChunk1.getCallback().thenAccept(future::complete));

                    //Start work
                    if (!this.isWorking()) {
                        this.setWorking(true);
                        this.startWork();
                    }
                    return future;
                }
            }

            //If not then add the queued chunk
            this.queue.add(queuedChunk);

            //Start work
            if (!this.isWorking()) {
                this.setWorking(true);
                this.startWork();
            }
        } finally {
            listLock.unlock();
        }
        return future;
    }

    public synchronized CompletableFuture<Void> queueChunks(List<AsyncChunk> chunks) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        listLock.lock();
        for(AsyncChunk chunk : chunks) {
            this.queueChunk(chunk, future);
        }
        listLock.unlock();
        return future;
    }

    private synchronized boolean isWorking() {
        return this.working;
    }

    private synchronized void setWorking(boolean value) {
        this.working = value;
    }

    public synchronized List<QueuedChunk> getQueueCopy() {
        listLock.lock();
        ArrayList<QueuedChunk> queuedChunks = new ArrayList<>(queue);
        listLock.unlock();
        return queuedChunks;
    }
}
