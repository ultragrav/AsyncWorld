package net.ultragrav.asyncworld;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class ChunkQueue implements Listener {
    public static int WORK_TIME_PER_TICK_MS = 20;
    public static int THREADS = Runtime.getRuntime().availableProcessors();
    ExecutorService executor = Executors.newCachedThreadPool();
    boolean useGC = false;
    @Getter
    private final Plugin plugin;
    private final List<QueuedChunk> queue = new ArrayList<>();
    private int taskId = -1;
    private long lastGC = System.currentTimeMillis();
    private boolean working;
    private final ReentrantLock listLock = new ReentrantLock(true);
    private long lastTick = -1;
    private long accTime = 0L;

    public ChunkQueue(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Update chunks
     *
     * @return List of AsyncChunks updated and to be removed from the queue
     */
    protected List<Runnable> update(List<QueuedChunk> chunks, ReentrantLock listLock, long time) {
        if (chunks.size() == 0)
            return new ArrayList<>();
        long ms = System.currentTimeMillis();

        if (chunks.size() > 200) {
            useGC = true;
        }

        List<Runnable> callbacks = new ArrayList<>();

        if (THREADS == 1) {
            while (System.currentTimeMillis() - ms < time) {
                listLock.lock();
                AsyncChunk chunk;
                try {
                    if (chunks.size() > 0) {
                        chunk = chunks.get(0).getChunk();
                        callbacks.addAll(chunks.get(0).getCallbacks());
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
                int amount = 0;
                long ms1 = System.currentTimeMillis();
                List<CompletableFuture<AsyncChunk>> futures = new ArrayList<>();
                while (System.currentTimeMillis() - ms < time && !shouldStop()) {
                    Map<AsyncChunk, Integer> masks = new ConcurrentHashMap<>();

                    List<AsyncChunk> todo = new ArrayList<>();

                    //Get chunks
                    //Lock it so that while we schedule the tasks, the list isn't changed
                    listLock.lock();
                    try {
                        for (int i = 0; i < THREADS && chunks.size() > 0 && System.currentTimeMillis() - ms < time && !shouldStop(); i++) { // Spawn THREAD threads
                            AsyncChunk chunk = chunks.get(0).getChunk();
                            callbacks.addAll(chunks.get(0).getCallbacks());
                            chunks.remove(0);
                            todo.add(chunk);
                            amount++;
                        }
                    } finally {
                        listLock.unlock(); //Unlock
                    }

                    //Schedule
                    todo.removeIf(chunk -> {
                        chunk.start();

                        CompletableFuture<AsyncChunk> future = new CompletableFuture<>();

                        //NOTE this is a just the scheduling of the task, not the execution, so this doesn't take long
                        ForkJoinPool.commonPool().execute(() -> {
                            try {
                                synchronized (chunk) { //synchronized so the editedSections is correct
                                    masks.put(chunk, chunk.getEditedSections());
                                    chunk.call();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                future.complete(chunk);
                            }
                        });
                        futures.add(future);
                        return true;
                    });

                    //Wait
                    Iterator<CompletableFuture<AsyncChunk>> futureIterator = futures.iterator();
                    CompletableFuture<AsyncChunk> future;
                    while (futureIterator.hasNext()) {
                        future = futureIterator.next();
                        AsyncChunk chunk = future.get();
                        chunk.end(masks.get(chunk));
                        futureIterator.remove();
                    }

                    listLock.lock();
                    if (chunks.isEmpty()) {
                        listLock.unlock();
                        break;
                    }
                    listLock.unlock();
                }

                accTime += (System.currentTimeMillis() - ms1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (chunks.isEmpty()) {
            //Got rid of cancelTask so this runs every tick
            //That way if a chunk is scheduled it could be updated on the same tick instead of 1 tick later

            if (!working) {
                cancelTask();
            }

            //GC
            if (Runtime.getRuntime().freeMemory() / (double) Runtime.getRuntime().totalMemory() < 0.20D
                    || System.currentTimeMillis() - lastGC > 20000 || useGC) {
                System.gc();
                useGC = false;
                lastGC = System.currentTimeMillis();
            }

            accTime = 0;
        }

        lastTick = System.currentTimeMillis();

        return callbacks;
    }

    @EventHandler
    private void onDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin))
            cancelTask();
    }

    public synchronized void cancelTask() {
        listLock.lock();
        try {
            if (taskId == -1) {
                return;
            }
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        } finally {
            listLock.unlock();
        }
    }

    public synchronized void startWork() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            this.update(queue, listLock, WORK_TIME_PER_TICK_MS).forEach(c -> {
                if (c == null)
                    return;
                try {
                    c.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        }, 1, 1);
    }

    public int getQueueSize() {
        try {
            return this.queue.size();
        } finally {
            this.listLock.lock();
        }
    }

    public boolean queueChunk(AsyncChunk chunk) {
        return queueChunk(chunk, null);
    }

    /**
     * Queues a chunk for flushing
     *
     * @param chunk    The chunk to queue
     * @param callback callback to run when chunk has been flushed
     * @return true if successfully queued, false otherwise
     */
    public boolean queueChunk(AsyncChunk chunk, Runnable callback) {
        if (chunk.getParent() == null || chunk.getParent().getBukkitWorld() == null)
            return false;
        listLock.lock();
        try {
            QueuedChunk queuedChunk = new QueuedChunk(chunk, callback);

            //Check if we should merge
            for (QueuedChunk queuedChunk1 : queue) {
                if (queuedChunk1.getChunk().getLoc().equals(chunk.getLoc())) {

                    //Merge Chunks
                    queuedChunk1.getChunk().merge(chunk);

                    //Merge callbacks
                    queuedChunk1.addCallback(callback);

                    //Start work
                    if (!this.isWorking()) {
                        this.setWorking(true);
                        this.startWork();
                    }
                    return true;
                }
            }

            //If not then add the queued chunk
            this.queue.add(queuedChunk);

            //Start work
            if (!this.isWorking()) {
                this.setWorking(true);
                this.startWork();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            listLock.unlock();
        }
        return true;
    }

    public synchronized void queueChunks(List<AsyncChunk> chunks, Runnable callback) {
        AtomicInteger completed = new AtomicInteger(0);
        chunks = new ArrayList<>(chunks);
        final int needed = chunks.size();
        final AtomicBoolean ran = new AtomicBoolean(false);
        listLock.lock();
        try {
            for (AsyncChunk chunk : chunks) {
                if (!this.queueChunk(chunk, () -> {
                    if (completed.incrementAndGet() >= needed && ran.compareAndSet(false, true)) {
                        callback.run();
                    }
                })) {

                    //Failed to queue, count it as completed
                    if (completed.incrementAndGet() >= needed && ran.compareAndSet(false, true)) {
                        callback.run();
                    }
                }
            }
        } finally {
            listLock.unlock();
        }
    }

    private synchronized boolean isWorking() {
        return this.working;
    }

    public synchronized void setWorking(boolean value) {
        this.working = value;
    }

    public synchronized List<QueuedChunk> getQueueCopy() {
        listLock.lock();
        ArrayList<QueuedChunk> queuedChunks = new ArrayList<>(queue);
        listLock.unlock();
        return queuedChunks;
    }

    private boolean shouldStop() {
        if (this.lastTick == -1) {
            this.lastTick = System.currentTimeMillis();
            return false;
        }
        return System.currentTimeMillis() - lastTick + WORK_TIME_PER_TICK_MS < 50;
    }
}
