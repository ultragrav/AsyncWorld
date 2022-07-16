package net.ultragrav.asyncworld.customworld;

import lombok.Getter;
import net.ultragrav.asyncworld.AsyncChunk;
import net.ultragrav.asyncworld.ChunkLocation;
import net.ultragrav.asyncworld.scheduler.SyncScheduler;
import net.ultragrav.asyncworld.schematics.Schematic;
import net.ultragrav.nbt.wrapper.TagCompound;
import net.ultragrav.utils.CuboidRegion;
import net.ultragrav.utils.IntVector3D;
import net.ultragrav.utils.Vector3D;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Async safe world
 * Also works as a sort-of edit queue
 */
public class SpigotCustomWorldAsyncWorld extends CustomWorldAsyncWorld {

    private static ExecutorService executor = Executors.newCachedThreadPool();

    @Getter
    private SpigotCustomWorldChunkMap chunkMap = new SpigotCustomWorldChunkMap(this);

    //private BunkerWorldServer worldServer;
    private final Plugin plugin;
    private final CustomWorld world;

    public SpigotCustomWorldAsyncWorld(CustomWorld world, Plugin plugin) {
        this.plugin = plugin;
        this.world = world;
    }

    @Override
    public World getBukkitWorld() {
        return world.getBukkitWorld();
    }

    public CustomWorldAsyncChunk<?> getChunk(int cx, int cz) {
        return chunkMap.getOrMake(cx, cz);
    }

    protected CustomWorldAsyncChunk<?> getNewChunk(int cx, int cz) {
        int sV = SpigotCustomWorld.getServerVersionInt();
        if (sV == 1) {
            return new CustomWorldAsyncChunk1_12(this, new ChunkLocation(this, cx, cz)); //TODO
        }
        throw new RuntimeException("Server version not supported!");
    }

    public int syncGetBlock(int x, int y, int z) {
        return getChunk(x >> 4, z >> 4).getCombinedBlockSync(x & 15, y, z & 15);
    }

    @Override
    public void syncSetBlock(int x, int y, int z, int id, int data) {
        getChunk(x >> 4, z >> 4).setCombinedBlockSync(x & 15, y, z & 15, data << 12 | id);
    }

    /**
     * Gets the cached block at the specified location<br>NOTE: the block might be -1 if you have not called refresh()
     *
     * @see CustomWorldAsyncChunk<?>#refresh(int)
     * @see #syncFastRefreshChunksInRegion(CuboidRegion, int)
     */
    public int getCachedBlock(int x, int y, int z) {
        CustomWorldAsyncChunk<?> chunk = getChunk(x >> 4, z >> 4);
        return chunk.readBlock(x & 15, y, z & 15);
    }

    public void pasteSchematic(Schematic schematic, IntVector3D position) {
        pasteSchematic(schematic, position, false);
    }

    public void pasteSchematic(Schematic schematic, IntVector3D position, boolean ignoreAir) {
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

        int threads = schematic.getDimensions().getArea() > 50000 ? Runtime.getRuntime().availableProcessors() : 1;

        ForkJoinPool pool = threads == 1 ? null : new ForkJoinPool(threads);

        for (int x1 = minX; x1 <= maxX; x1++)
            for (int z1 = minZ; z1 <= maxZ; z1++) {
                int finalZ = z1;
                int finalX = x1;
                Runnable runnable = () -> {
                    CustomWorldAsyncChunk<?> chunk = getChunk(finalX, finalZ);
                    int cxi = chunk.getLoc().getX() << 4;
                    int czi = chunk.getLoc().getZ() << 4;

                    int chunkMinX = Math.max(absMinX - cxi, 0);
                    int chunkMinZ = Math.max(absMinZ - czi, 0);

                    int chunkMaxX = Math.min(absMaxX - cxi, 15);
                    int chunkMaxZ = Math.min(absMaxZ - czi, 15);

                    try {
                        for (int y = absMinY; y <= absMaxY; y++) {
                            int sy = y - posY;
                            for (int x = chunkMinX; x <= chunkMaxX; x++) {
                                int sx = cxi + x - posX;
                                for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                                    int sz = czi + z - posZ;
                                    int block = schematic.getBlockAt(sx, sy, sz);
                                    if (block == -1)
                                        continue;
                                    if (!ignoreAir || block != 0) {
                                        chunk.setBlock(x, y, z, block, false);
                                        chunk.setEmittedLight(x, y, z, schematic.getEmittedLight(sx, sy, sz));
                                    } else {
                                        int lighting = schematic.getEmittedLight(sx, sy, sz);
                                        if (lighting != 0)
                                            chunk.setEmittedLight(x, y, z, lighting);
                                    }
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

        IntVector3D finalPosition = position;
        schematic.getTiles().forEach((p, t) -> setTile(p.getX() + finalPosition.getX(), p.getY() + finalPosition.getY(), p.getZ() + finalPosition.getZ(), t));

        if (threads != 1) {
            pool.shutdown();
            while (!pool.isTerminated()) {
                try {
                    pool.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void setBlocks(CuboidRegion region, Supplier<Short> blockSupplier) {
        //small edit
        for (int x = region.getMinimumPoint().getBlockX(); x <= region.getMaximumPoint().getX(); x++) {
            for (int z = region.getMinimumPoint().getBlockZ(); z <= region.getMaximumPoint().getZ(); z++) {
                CustomWorldAsyncChunk<?> currentChunk = getChunk(x >> 4, z >> 4);
                for (int y = region.getMinimumPoint().getBlockY(); y <= region.getMaximumPoint().getY(); y++) {
                    int block = blockSupplier.get();
                    currentChunk.writeBlock(x & 15, y, z & 15, block & 4095, (byte) (block >>> 12));
                }
            }
        }
    }

    public void setBiome(int x, int z, int biome) {
        CustomWorldAsyncChunk<?> chunk = getChunk(x >> 4, z >> 4);
        chunk.setBiome(x & 15, z & 15, biome);
    }

    public void syncForAllInRegion(CuboidRegion region, BiConsumer<IntVector3D, Integer> action, boolean multiThread) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getLogger().info("Called syncForAllInRegion from asynchronous thread!");
            return;
        }
        if (region == null)
            return;

        long ms = System.currentTimeMillis();

        int threads = Runtime.getRuntime().availableProcessors();

        if (threads == 1 || !multiThread) {
            for (int x = region.getMinimumPoint().getBlockX(); x <= region.getMaximumPoint().getBlockX(); x++) {
                for (int z = region.getMinimumPoint().getBlockZ(); z <= region.getMaximumPoint().getBlockZ(); z++) {
                    CustomWorldAsyncChunk<?> chunk = getChunk(x >> 4, z >> 4);
                    if (!chunk.isChunkLoaded())
                        chunk.getBukkitChunk().load(true);
                    for (int y = region.getMinimumPoint().getBlockY(); y <= region.getMaximumPoint().getBlockY(); y++) {
                        int block = chunk.getCombinedBlockSync(x & 15, y, z & 15);
                        action.accept(new IntVector3D(x, y, z), block);
                    }
                }
            }
        } else {
            List<CustomWorldAsyncChunk<?>> chunks = new ArrayList<>();

            Vector3D max = region.getMaximumPoint();
            Vector3D min = region.getMinimumPoint();
            int minI = min.getBlockX() >> 4;
            int minJ = min.getBlockZ() >> 4;
            int maxI = max.getBlockX() >> 4;
            int maxJ = max.getBlockZ() >> 4;

            for (int i = minI; i <= maxI; i++)
                for (int j = minJ; j <= maxJ; j++)
                    chunks.add(getChunk(i, j));
            int minBlockY = min.getBlockY();
            int minBlockX = min.getBlockX();
            int minBlockZ = min.getBlockZ();
            int maxBlockY = max.getBlockY();
            int maxBlockX = max.getBlockX();
            int maxBlockZ = max.getBlockZ();

            Iterator<CustomWorldAsyncChunk<?>> it = chunks.iterator();
            List<Future<Void>> futures = new ArrayList<>();
            while (it.hasNext()) {
                for (int i = 0; i < threads && it.hasNext(); i++) {
                    CustomWorldAsyncChunk<?> chunk = it.next();
                    chunk.getBukkitChunk().load(true);
                    futures.add(executor.submit(() -> {
                        int bx = chunk.getLoc().getX() << 4;
                        int bz = chunk.getLoc().getZ() << 4;
                        for (int x = Math.max(bx, minBlockX) & 15; x < 16 && x + bx <= maxBlockX; x++) {
                            for (int z = Math.max(bz, minBlockZ) & 15; z < 16 && z + bz <= maxBlockZ; z++) {
                                for (int y = minBlockY; y <= maxBlockY; y++) {
                                    int block = chunk.getCombinedBlockSync(x, y, z);
                                    action.accept(new IntVector3D(x + bx, y, z + bz), block);
                                }
                            }
                        }
                        return null;
                    }));
                }
                futures.forEach(f -> {
                    if (f == null)
                        return;
                    try {
                        f.get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                });
                futures.clear();
            }
        }
    }

    @Override
    public void syncForAllInRegion(CuboidRegion cuboidRegion, AsyncWorldTriConsumer<IntVector3D, Integer, TagCompound> asyncWorldTriConsumer, boolean b) {

    }

    @Override
    public void asyncForAllInRegion(CuboidRegion cuboidRegion, AsyncWorldQuadConsumer<IntVector3D, Integer, TagCompound, Integer> asyncWorldQuadConsumer, boolean b) {

    }

    @Override
    public void setTile(int x, int y, int z, TagCompound tagCompound) {
        CustomWorldAsyncChunk<?> chunk = getChunk(x >> 4, z >> 4);
        chunk.setTileEntity(x & 15, y, z & 15, tagCompound);
    }

    public void setBlock(int x, int y, int z, int id, byte data) {
        CustomWorldAsyncChunk<?> chunk = getChunk(x >> 4, z >> 4);
        chunk.writeBlock(x & 15, y, z & 15, id, data);
    }

    public void setIgnore(int x, int y, int z) {
        CustomWorldAsyncChunk<?> chunk = getChunk(x >> 4, z >> 4);
        chunk.setIgnore(x & 15, y, z & 15);
    }

    @Override
    public CompletableFuture<Void> flush() {
        return null;
    }

    @Override
    public CompletableFuture<Void> flush(boolean relight) {
        return null;
    }

    @Override
    public boolean syncFlush(int timeoutMs) {
        return false;
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
            pool.shutdown();
            while (!pool.isTerminated()) {
                try {
                    pool.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
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
    public void ensureChunkLoaded(AsyncChunk... chunks) {
        boolean sync = Bukkit.isPrimaryThread();
        List<AsyncChunk> syncLoad = new ArrayList<>();
        for (AsyncChunk chunk : chunks) {
            if (!chunk.isChunkLoaded()) {
                if (sync) {
                    getBukkitWorld().loadChunk(chunk.getLoc().getX(), chunk.getLoc().getZ());
                } else {
                    syncLoad.add(chunk);
                }
            }
        }
        if (!syncLoad.isEmpty()) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            SyncScheduler.sync(() -> {
                syncLoad.forEach(c -> getBukkitWorld().loadChunk(c.getLoc().getX(), c.getLoc().getZ()));
                future.complete(null);
            }, this.plugin);
            future.join();
        }
    }

    @Override
    public Schematic optimizedCreateSchematic(CuboidRegion region, IntVector3D origin, int ignoreBlock) {
        return new Schematic(origin, this, region, ignoreBlock);
    }
}