package net.ultragrav.asyncworld;

import lombok.Getter;
import net.ultragrav.asyncworld.chunk.AsyncChunk1_12_R1;
import net.ultragrav.asyncworld.chunk.AsyncChunk1_15_R1;
import net.ultragrav.asyncworld.chunk.AsyncChunk1_8_R3;
import net.ultragrav.asyncworld.nbt.TagCompound;
import net.ultragrav.asyncworld.relighter.NMSRelighter;
import net.ultragrav.asyncworld.relighter.Relighter;
import net.ultragrav.asyncworld.schematics.Schematic;
import net.ultragrav.serializer.GravSerializer;
import net.ultragrav.utils.CuboidRegion;
import net.ultragrav.utils.IntVector3D;
import net.ultragrav.utils.Vector3D;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Async safe world
 * Also works as a sort-of edit queue
 */
public class SpigotAsyncWorld extends AsyncWorld {
    private static ExecutorService executor = Executors.newCachedThreadPool();
    @Getter
    protected String serverVersion;
    private ChunkQueue chunkQueue;
    private UUID world;
    private ChunkMap chunkMap = new ChunkMap(this);
    private int sV = 0;
    private Relighter relighter;

    public SpigotAsyncWorld(World world) {
        this(world, GlobalChunkQueue.instance);
    }

    public SpigotAsyncWorld(World world, ChunkQueue chunkQueue) {
        this.world = world.getUID();
        this.chunkQueue = chunkQueue;
        this.relighter = new NMSRelighter(chunkQueue.getPlugin());

        String name = Bukkit.getServer().getClass().getName();
        String[] parts = name.split("\\.");
        serverVersion = parts[3];
        if (this.getServerVersion().startsWith("v1_8")) // TODO: Other 1.8 variants? v1_8_R1, R_2?
            sV = 8;
        if (this.getServerVersion().startsWith("v1_12"))
            sV = 12;
        if (this.getServerVersion().startsWith("v1_15"))
            sV = 15;
    }

    @Override
    public World getBukkitWorld() {
        return Bukkit.getWorld(world);
    }

    @Override
    public AsyncChunk getChunk(int cx, int cz) {
        return chunkMap.getOrMake(cx, cz);
    }

    @Override
    protected AsyncChunk getNewChunk(int cx, int cz) {
        switch (sV) {
            case 8:
                return new AsyncChunk1_8_R3(this, new ChunkLocation(this, cx, cz));
            case 12:
                return new AsyncChunk1_12_R1(this, new ChunkLocation(this, cx, cz));
            case 15:
                return new AsyncChunk1_15_R1(this, new ChunkLocation(this, cx, cz));
        }
        return null;
    }

    @Override
    public int syncGetBlock(int x, int y, int z) {
        return getChunk(x >> 4, z >> 4).getCombinedBlockSync(x & 15, y, z & 15) & 0xFFFF;
    }

    /**
     * Gets the cached block at the specified location<br>NOTE: the block might be -1 if you have not called refresh()
     *
     * @see AsyncChunk#refresh(int)
     * @see #syncFastRefreshChunksInRegion(CuboidRegion, int)
     */
    @Override
    public int getCachedBlock(int x, int y, int z) {
        AsyncChunk chunk = getChunk(x >> 4, z >> 4);
        return chunk.readBlock(x & 15, y, z & 15);
    }

    @Override
    public void pasteSchematic(Schematic schematic, IntVector3D position) {
        position = position.subtract(schematic.getOrigin());

        int posX = position.getX();
        int posY = position.getY();
        int posZ = position.getZ();

        IntVector3D max = position.add(schematic.getDimensions()).subtract(1, 1, 1);
        IntVector3D min = position;
        int minX = min.getX() >> 4;
        int minZ = min.getZ() >> 4;
        int maxX = max.getX() >> 4;
        int maxZ = max.getZ() >> 4;


        int absMinX = min.getX();
        int absMinY = min.getY();
        int absMinZ = min.getZ();

        int absMaxX = max.getX();
        int absMaxY = max.getY();
        int absMaxZ = max.getZ();

        int threads = schematic.getDimensions().getArea() > 200000 ? Runtime.getRuntime().availableProcessors() : 1;

        ForkJoinPool pool = threads == 1 ? null : new ForkJoinPool(threads);

        for (int x1 = minX; x1 <= maxX; x1++)
            for (int z1 = minZ; z1 <= maxZ; z1++) {
                int finalZ = z1;
                int finalX = x1;
                Runnable runnable = () -> {
                    AsyncChunk chunk = getChunk(finalX, finalZ);
                    int cxi = finalX << 4; //Chunk origin coordinates
                    int czi = finalZ << 4; //Chunk origin coordinates

                    int chunkMinX = Math.max(absMinX - cxi, 0);
                    int chunkMinZ = Math.max(absMinZ - czi, 0);

                    int chunkMaxX = Math.min(absMaxX - cxi, 15);
                    int chunkMaxZ = Math.min(absMaxZ - czi, 15);

                    cxi -= posX;
                    czi -= posZ;

                    try {
                        for (int x = chunkMinX; x <= chunkMaxX; x++) {
                            int interX = cxi + x;
                            for (int y = absMinY; y <= absMaxY; y++) {
                                int interY = y - posY;
                                for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                                    int block = schematic.getBlockAt(interX, interY, czi + z);
                                    if (block == -1)
                                        continue;
                                    chunk.writeBlock(x, y, z, block & 0xFFF, (byte) (block >>> 12 & 0xF), false);
                                    chunk.setEmittedLight(x, y, z, schematic.getEmittedLight(interX, interY, czi + z));
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                };
                if (threads != 1)
                    pool.submit(runnable);
                else
                    runnable.run();
            }

        if (threads != 1) {
            while (true) {
                if (pool.awaitQuiescence(1, TimeUnit.SECONDS)) break;
            }
            pool.shutdown();
        }
        IntVector3D finalPosition = position;
        schematic.getTiles().forEach((p, t) -> setTile(p.getX() + finalPosition.getX(), p.getY() + finalPosition.getY(), p.getZ() + finalPosition.getZ(), t));
    }

    @Override
    public void setBlocks(CuboidRegion region, Supplier<Short> blockSupplier) {
        //small edit
        if (region.getArea() < 1000000) {
            for (int x = region.getMinimumPoint().getBlockX(); x <= region.getMaximumPoint().getX(); x++) {
                for (int z = region.getMinimumPoint().getBlockZ(); z <= region.getMaximumPoint().getZ(); z++) {
                    AsyncChunk currentChunk = getChunk(x >> 4, z >> 4);
                    for (int y = region.getMinimumPoint().getBlockY(); y <= region.getMaximumPoint().getY(); y++) {
                        int block = blockSupplier.get();
                        currentChunk.writeBlock(x & 15, y, z & 15, block & 4095, (byte) (block >>> 12));
                    }
                }
            }
        } else {
            actionBlocks(region, (chunk, x, y, z) -> {
                int block = blockSupplier.get();
                chunk.writeBlock(x & 15, y, z & 15, block & 4095, (byte) (block >>> 12));
            }, Runtime.getRuntime().availableProcessors());
        }
    }

    @Override
    public void syncForAllInRegion(CuboidRegion region, BiConsumer<IntVector3D, Integer> action, boolean multiThread) {
        syncForAllInRegion(region, (a, b, c) -> action.accept(a, b), multiThread);
    }

    @Override
    public void syncForAllInRegion(CuboidRegion region, AsyncWorldTriConsumer<IntVector3D, Integer, TagCompound> action, boolean multiThread) {
        boolean isSync = Bukkit.isPrimaryThread();

        int mask = 0;
        int low = region.getMinimumY() >> 4;
        int high = region.getMaximumY() >> 4;
        for (int i = low; i <= high; i++) {
            mask |= 1 << i;
        }

        int finalMask = mask;
        Runnable runnable = () -> {
            int threads = multiThread ? Runtime.getRuntime().availableProcessors() : 1;
            actionBlocks(region, (chunk, x, y, z) -> action.accept(new IntVector3D(x, y, z), chunk.readBlock(x & 15, y, z & 15), chunk.getTile(x & 15, y, z & 15)), (c) -> c.refresh(finalMask), threads, true);
        };
        if (isSync) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(chunkQueue.getPlugin(), runnable);
        }
    }

    @Override
    public void asyncForAllInRegion(CuboidRegion region, AsyncWorldQuadConsumer<IntVector3D, Integer, TagCompound, Integer> action, boolean multiThread) {
        boolean isSync = Bukkit.isPrimaryThread();

        CompletableFuture<Void> f = new CompletableFuture<>();
        Runnable refresh = () -> {

            //Create mask
            int bottom = region.getMinimumY();
            int section = bottom >> 4;
            int otherSection = region.getMaximumY();
            int mask = 0;
            for (int i = section; i <= otherSection; i++) {
                mask |= 1 << i;
            }
            int finalMask = mask;

            //Refresh chunks with mask

            actionChunks(region, (c) -> c.refresh(finalMask), Runtime.getRuntime().availableProcessors(), true);

            //Complete future
            f.complete(null);
        };
        if (isSync) {
            refresh.run();
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    refresh.run();
                }
            }.runTask(chunkQueue.getPlugin());
        }
        f.join();

        //Perform actions
        int threads = multiThread ? Runtime.getRuntime().availableProcessors() : 1;
        actionBlocks(region, (c, x, y, z) -> action.accept(new IntVector3D(x, y, z), c.readBlock(x & 15, y, z & 15), c.getTile(x & 15, y, z & 15), c.syncGetEmittedLight(x & 15, y, z & 15)), threads);
    }

    @Override
    public void setTile(int x, int y, int z, TagCompound tag) {
        AsyncChunk chunk = getChunk(x >> 4, z >> 4);
        chunk.setTileEntity(x & 15, y, z & 15, tag);
    }

    @Override
    public void setBiome(int x, int z, int value) {
        getChunk(x >> 4, z >> 4).setBiome(x & 15, z & 15, value);
    }

    @Override
    public void setBlock(int x, int y, int z, int id, byte data) {
        AsyncChunk chunk = getChunk(x >> 4, z >> 4);
        chunk.writeBlock(x & 15, y, z & 15, id, data);
    }

    @Override
    public void setIgnore(int x, int y, int z) {
        AsyncChunk chunk = getChunk(x >> 4, z >> 4);
        chunk.setIgnore(x & 15, y, z & 15);
    }

    /**
     * Completes the operation synchronously, but fast
     *
     * @return Whether or not the operation completed before the timeout OR FALSE if the current thread is not the server main thread
     */
    @Override
    public synchronized boolean syncFlush(int timeoutMs) {
        if (!Bukkit.isPrimaryThread()) {
            System.out.println("Not primary thread");
            return false;
        }
        List<AsyncChunk> edited = chunkMap.getCachedCopy();
        System.out.println(edited.size() + " edited chunks?");
        edited.removeIf(c -> {
            c.optimize();
            return !c.isEdited();
        });
        System.out.println("Now there is " + edited.size());
        List<QueuedChunk> queue = edited.stream().map(QueuedChunk::new).collect(Collectors.toList());
        System.out.println("Calling updated with queue of size " + queue.size());
        chunkQueue.update(queue, new ReentrantLock(true), timeoutMs);
        this.chunkMap.clear();
        return edited.isEmpty();
    }

    @Override
    public CompletableFuture<Void> flush() {
        return this.flush(true);
    }

    @Override
    public CompletableFuture<Void> flush(boolean relight) {
        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        CompletableFuture<Void> future = new CompletableFuture<>();

        Runnable runnable = () -> {
            try {
                List<AsyncChunk> edited;
                synchronized (this) {
                    edited = chunkMap.getCachedCopy();
                    this.chunkMap.clear();
                }

                //Optimize
                if (edited.size() >= 64) {
                    long ms = System.currentTimeMillis();
                    edited.forEach(c -> pool.submit(c::optimize));
                    while (true) if (pool.awaitQuiescence(1, TimeUnit.SECONDS)) break;
                    ms = System.currentTimeMillis() - ms;
                } else {
                    edited.forEach(AsyncChunk::optimize);
                }


                Map<AsyncChunk, Integer> masks = new HashMap<>();
                for (AsyncChunk chunk : edited) {
                    if (relight) {
                        int editedSections = chunk.getEditedSections();
                        for (int i = 0; i < 16; i++) {
                            boolean a = ((editedSections >>> i) & 1) != 0;
                            if (a && i != 0) {
                                editedSections |= 1 << (i - 1);
                            }
                            if (a && i != 15) {
                                editedSections |= 1 << (++i);
                            }
                        }
                        masks.put(chunk, editedSections);
                    } else {
                        chunk.setFullSkyLight(true);
                    }
                }
                //Queue
                chunkQueue.queueChunks(edited, () -> {
                    edited.forEach(c -> {
                        if (relight) {
                            int mask = masks.get(c);
                            Relighter.RelightAction[] actions = new Relighter.RelightAction[16];
                            for (int i = 0; i < 16; i++) {
                                if ((mask >>> i & 1) == 1) {
                                    actions[i] = Relighter.RelightAction.ACTION_RELIGHT;
                                } else {
                                    actions[i] = Relighter.RelightAction.ACTION_SKIP_AIR;
                                }
                            }
                            relighter.queueSkyRelight(c, actions);
                        }
                    }); //Schedule relighting
                    future.complete(null);
                });
            } catch (Throwable t) {
                t.printStackTrace();
            }
            pool.shutdown();
        };

        if (Bukkit.isPrimaryThread()) {
            pool.execute(runnable);
        } else {
            runnable.run();
        }

        return future;
    }

    @Override
    public void ensureChunkLoaded(AsyncChunk... chunks) {
        boolean sync = Bukkit.isPrimaryThread();
        List<AsyncChunk> syncLoad = new ArrayList<>();
        for (AsyncChunk chunk : chunks) {
            if (!chunk.isChunkLoaded()) {
                if (sync) {
                    getBukkitWorld().loadChunk(chunk.getLoc().getX(), chunk.getLoc().getZ(), true);
                } else {
                    syncLoad.add(chunk);
                }
            }
        }
        if (!syncLoad.isEmpty()) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            new BukkitRunnable() {
                @Override
                public void run() {
                    syncLoad.forEach(c -> getBukkitWorld().loadChunk(c.getLoc().getX(), c.getLoc().getZ(), true));
                    future.complete(null);
                }
            }.runTask(chunkQueue.getPlugin());
            future.join();
        }
    }

    @Override
    public int syncGetBrightnessOpacity(int x, int y, int z) {
        return getChunk(x >> 4, z >> 4).syncGetBrightnessOpacity(x & 15, y, z & 15);
    }

    @Override
    public void syncSetEmittedLight(int x, int y, int z, int value) {
        getChunk(x >> 4, z >> 4).syncSetEmittedLight(x & 15, y, z & 15, value);
    }

    @Override
    public void syncSetSkyLight(int x, int y, int z, int value) {
        getChunk(x >> 4, z >> 4).syncSetSkyLight(x & 15, y, z & 15, value);
    }

    @Override
    public int syncGetEmittedLight(int x, int y, int z) {
        return getChunk(x >> 4, z >> 4).syncGetEmittedLight(x & 15, y, z & 15);
    }

    @Override
    public int syncGetSkyLight(int x, int y, int z) {
        return getChunk(x >> 4, z >> 4).syncGetSkyLight(x & 15, y, z & 15);
    }

    @Override
    public Schematic optimizedCreateSchematic(CuboidRegion region, IntVector3D origin, int ignoreBlock) {
        return new Schematic(origin, this, region);
    }

    /**
     * Loads async chunks in a region and refreshes them with the current
     * contents of the chunk (that must be done by calling refresh and is
     * not called on creation) see refresh on AsyncChunk for more info <br>
     * This function will attempt to use multi-threading if more than one
     * core is available
     *
     * @param region  The cuboid region
     * @param timeout the timeout in ms, may be set to -1 for no timeout
     * @see AsyncChunk#refresh(int)
     */
    @Override
    public synchronized void syncFastRefreshChunksInRegion(CuboidRegion region, int timeout) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getLogger().info("Called syncFastRefreshChunkInRegion from asynchronous thread!");
            return;
        }
        if (region == null)
            return;

        long ms = System.currentTimeMillis();

        List<AsyncChunk> chunks = new ArrayList<>();

        Vector3D max = region.getMaximumPoint();
        Vector3D min = region.getMinimumPoint();
        int minI = min.getBlockX() >> 4;
        int minJ = min.getBlockZ() >> 4;
        int maxI = max.getBlockX() >> 4;
        int maxJ = max.getBlockZ() >> 4;

        for (int i = minI; i <= maxI; i++)
            for (int j = minJ; j <= maxJ; j++)
                chunks.add(getChunk(i, j));

        int threads = chunks.size() >= Runtime.getRuntime().availableProcessors() ? Runtime.getRuntime().availableProcessors() : 1;
        if (chunks.size() < 36)
            threads = 1;

        int sectionMask = 0;

        int minY = region.getMinimumY() >> 4;
        int maxY = region.getMaximumY() >> 4;
        for (int i = minY; i <= maxY; i++) {
            sectionMask |= 1 << i;
        }

        int finalSectionMask = sectionMask;

        if (threads == 1) {
            chunks.forEach(c -> {
                if (!c.isChunkLoaded())
                    c.getBukkitChunk().load(true);
                c.refresh(finalSectionMask);
            });
        } else {
            Iterator<AsyncChunk> it = chunks.iterator();
            ForkJoinPool pool = new ForkJoinPool(threads);
            while (it.hasNext() && !(timeout != -1 && System.currentTimeMillis() - ms > timeout)) {
                for (int i = 0; i < threads && it.hasNext(); i++) {
                    AsyncChunk chunk = it.next();
                    if (!chunk.isChunkLoaded())
                        chunk.getBukkitChunk().load(true);
                    Runnable runnable = () -> chunk.refresh(finalSectionMask);
                    pool.submit(runnable);
                }
            }
            while (!pool.isQuiescent())
                pool.awaitQuiescence(1, TimeUnit.SECONDS);
            pool.shutdown();
        }
    }

    private static interface ActionBlockConsumer {
        void accept(AsyncChunk chunk, int x, int y, int z);
    }

    private void actionBlocks(CuboidRegion region, ActionBlockConsumer action, int parallelism) {
        actionBlocks(region, action, null, parallelism, false);
    }

    private void actionBlocks(CuboidRegion region, ActionBlockConsumer action, Consumer<AsyncChunk> chunkPreprocessor, int parallelism, boolean ensureLoaded) {
        List<AsyncChunk> chunks = new ArrayList<>();

        Vector3D max = region.getMaximumPoint();
        Vector3D min = region.getMinimumPoint();
        int minX = min.getBlockX() >> 4;
        int minZ = min.getBlockZ() >> 4;
        int maxX = max.getBlockX() >> 4;
        int maxZ = max.getBlockZ() >> 4;

        for (int i = minX; i <= maxX; i++)
            for (int j = minZ; j <= maxZ; j++)
                chunks.add(getChunk(i, j));

        if (ensureLoaded)
            ensureChunkLoaded(chunks.toArray(new AsyncChunk[0]));

        if (parallelism > 1) {
            ForkJoinPool pool = new ForkJoinPool(parallelism);
            int minBlockY = Math.max(0, min.getBlockY());
            int minBlockX = min.getBlockX();
            int minBlockZ = min.getBlockZ();
            int maxBlockY = Math.min(255, max.getBlockY());
            int maxBlockX = max.getBlockX();
            int maxBlockZ = max.getBlockZ();

            chunks.forEach(chunk -> pool.submit(() -> {

                if (chunkPreprocessor != null) {
                    try {
                        chunkPreprocessor.accept(chunk);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                int bx = chunk.getLoc().getX() << 4;
                int bz = chunk.getLoc().getZ() << 4;
                for (int x = Math.max(bx, minBlockX) & 15; x < 16 && x + bx <= maxBlockX; x++) {
                    for (int z = Math.max(bz, minBlockZ) & 15; z < 16 && z + bz <= maxBlockZ; z++) {
                        for (int y = minBlockY; y <= maxBlockY; y++) {
                            try {
                                action.accept(chunk, x + bx, y, z + bz);
                            } catch(Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                return null;
            }));
            while (!pool.isQuiescent())
                pool.awaitQuiescence(1, TimeUnit.SECONDS);
            pool.shutdown();
        } else {

            if (chunkPreprocessor != null)
                chunks.forEach(chunkPreprocessor);

            for (int x = region.getMinimumPoint().getBlockX(); x <= region.getMaximumPoint().getBlockX(); x++) {
                for (int z = region.getMinimumPoint().getBlockZ(), maxZ1 = region.getMaximumPoint().getBlockZ(); z <= maxZ1; z++) {
                    AsyncChunk chunk = getChunk(x >> 4, z >> 4);
                    for (int y = region.getMinimumPoint().getBlockY(), maxY = region.getMaximumY(); y <= maxY; y++) {
                        try {
                            action.accept(chunk, x, y, z);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private void actionChunks(CuboidRegion region, Consumer<AsyncChunk> action, int parallelism) {
        actionChunks(region, action, parallelism, false);
    }

    private void actionChunks(CuboidRegion region, Consumer<AsyncChunk> action, int parallelism, boolean ensureLoaded) {
        List<AsyncChunk> chunks = new ArrayList<>();

        long ms = System.currentTimeMillis();

        Vector3D max = region.getMaximumPoint();
        Vector3D min = region.getMinimumPoint();
        int minI = min.getBlockX() >> 4;
        int minJ = min.getBlockZ() >> 4;
        int maxI = max.getBlockX() >> 4;
        int maxJ = max.getBlockZ() >> 4;

        for (int i = minI; i <= maxI; i++)
            for (int j = minJ; j <= maxJ; j++)
                chunks.add(getChunk(i, j));

        if (ensureLoaded)
            ensureChunkLoaded(chunks.toArray(new AsyncChunk[0]));

        if (parallelism == 1) {
            chunks.forEach(action);
        } else {
            Iterator<AsyncChunk> it = chunks.iterator();
            ForkJoinPool pool = new ForkJoinPool(parallelism);
            while (it.hasNext()) {
                for (int i = 0; i < parallelism && it.hasNext(); i++) {
                    AsyncChunk chunk = it.next();
                    Runnable runnable = () -> {
                        try {
                            action.accept(chunk);
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    };
                    pool.submit(runnable);
                }
            }
            while (!pool.isQuiescent())
                pool.awaitQuiescence(1, TimeUnit.SECONDS);
            pool.shutdown();
        }
    }
}
