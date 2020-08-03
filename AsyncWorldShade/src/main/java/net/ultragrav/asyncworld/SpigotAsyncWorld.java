package net.ultragrav.asyncworld;

import lombok.Getter;
import net.ultragrav.asyncworld.chunk.AsyncChunk1_12_R1;
import net.ultragrav.asyncworld.chunk.AsyncChunk1_8_R3;
import net.ultragrav.asyncworld.nbt.TagCompound;
import net.ultragrav.asyncworld.schematics.Schematic;
import net.ultragrav.utils.CuboidRegion;
import net.ultragrav.utils.IntVector3D;
import net.ultragrav.utils.Vector3D;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
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

    public SpigotAsyncWorld(World world) {
        this(world, GlobalChunkQueue.instance);
    }

    public SpigotAsyncWorld(World world, ChunkQueue chunkQueue) {
        this.world = world.getUID();
        this.chunkQueue = chunkQueue;

        String name = Bukkit.getServer().getClass().getName();
        String[] parts = name.split("\\.");
        serverVersion = parts[3];
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
        if (this.getServerVersion().startsWith("v1_12"))
            return new AsyncChunk1_12_R1(this, new ChunkLocation(this, cx, cz));
        if (this.getServerVersion().startsWith("v1_8"))
            return new AsyncChunk1_8_R3(this, new ChunkLocation(this, cx, cz));
        return null;
    }

    @Override
    public int syncGetBlock(int x, int y, int z) {
        return getChunk(x >> 4, z >> 4).getCombinedBlockSync(x & 15, y, z & 15);
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

        int maxX = posX + schematic.getDimensions().getX();
        int maxY = posY + schematic.getDimensions().getY();
        int maxZ = posZ + schematic.getDimensions().getZ();

        for (int x = posX; x < maxX; x++) {
            for (int z = posZ; z < maxZ; z++) {
                AsyncChunk currentChunk = getChunk(x >> 4, z >> 4);
                for (int y = posY; y < maxY; y++) {
                    int block = schematic.getBlockAt(x - posX, y - posY, z - posZ);
                    //Bukkit.broadcastMessage("(" + x + ", " + y + ", " + z + ") -> " + block);
                    currentChunk.writeBlock(x & 0xF, y, z & 0xF, block & 0xFFF, (byte) (block >>> 12));
                }
            }
        }

        //Set tiles
        IntVector3D finalPosition = position;
        schematic.getTiles().forEach((p, t) -> {
            setTile(p.getX() + posX, p.getY() + posY, p.getZ() + posZ, t);
        });
    }

    @Override
    public void setBlocks(CuboidRegion region, Supplier<Short> blockSupplier) {
        if (region.getArea() < 10000) {
            //small edit
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
            //medium - very large edit

            //Get Chunks it intersects
            Vector3D max = region.getMaximumPoint();
            Vector3D min = region.getMinimumPoint();
            int minI = min.getBlockX() >> 4;
            int minJ = min.getBlockZ() >> 4;
            int maxI = max.getBlockX() >> 4;
            int maxJ = max.getBlockZ() >> 4;

            AsyncChunk.CuboidEdit edit = new AsyncChunk.CuboidEdit(region, blockSupplier);

            for (int i = minI; i <= maxI; i++) {
                for (int j = minJ; j <= maxJ; j++) {
                    AsyncChunk chunk = getChunk(i, j);
                    chunk.addCuboidEdit(edit);
                }
            }
        }
    }

    @Override
    public void syncForAllInRegion(CuboidRegion region, BiConsumer<Vector3D, Integer> action, boolean multiThread) {
        syncForAllInRegion(region, (a, b, c) -> action.accept(a, b), multiThread);
    }

    @Override
    public void syncForAllInRegion(CuboidRegion region, AsyncWorldTriConsumer<Vector3D, Integer, TagCompound> action, boolean multiThread) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getLogger().info("Called syncForAllInRegion from asynchronous thread!");
            return;
        }
        if (region == null)
            return;

        long ms = System.currentTimeMillis();

        this.syncFastRefreshChunksInRegion(region, 100000);

        int threads = Runtime.getRuntime().availableProcessors();

        if (threads == 1 || !multiThread) {
            for (int x = region.getMinimumPoint().getBlockX(); x <= region.getMaximumPoint().getBlockX(); x++) {
                for (int z = region.getMinimumPoint().getBlockZ(); z <= region.getMaximumPoint().getBlockZ(); z++) {
                    AsyncChunk chunk = getChunk(x >> 4, z >> 4);
                    if (!chunk.isChunkLoaded())
                        chunk.getBukkitChunk().load(true);
                    for (int y = region.getMinimumPoint().getBlockY(); y <= region.getMaximumPoint().getBlockY(); y++) {
                        int block = chunk.readBlock(x & 15, y, z & 15);
                        action.accept(new Vector3D(x, y, z), block, chunk.getTile(x & 15, y, z & 15));
                    }
                }
            }
        } else {
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
            int minBlockY = min.getBlockY();
            int minBlockX = min.getBlockX();
            int minBlockZ = min.getBlockZ();
            int maxBlockY = max.getBlockY();
            int maxBlockX = max.getBlockX();
            int maxBlockZ = max.getBlockZ();

            Iterator<AsyncChunk> it = chunks.iterator();
            List<Future<Void>> futures = new ArrayList<>();
            while (it.hasNext()) {
                for (int i = 0; i < threads && it.hasNext(); i++) {
                    AsyncChunk chunk = it.next();
                    chunk.getBukkitChunk().load(true);
                    futures.add(executor.submit(() -> {
                        int bx = chunk.getLoc().getX() << 4;
                        int bz = chunk.getLoc().getZ() << 4;
                        for (int x = Math.max(bx, minBlockX) & 15; x < 16 && x + bx <= maxBlockX; x++) {
                            for (int z = Math.max(bz, minBlockZ) & 15; z < 16 && z + bz <= maxBlockZ; z++) {
                                for (int y = minBlockY; y <= maxBlockY; y++) {
                                    int block = chunk.readBlock(x, y, z);
                                    action.accept(new Vector3D(x + (chunk.getLoc().getX() << 4), y, z + (chunk.getLoc().getZ() << 4)), block, chunk.getTile(x, y, z));
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
    public void setTile(int x, int y, int z, TagCompound tag) {
        AsyncChunk chunk = getChunk(x >> 4, z >> 4);
        chunk.setTileEntity(x & 15, y, z & 15, tag);
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

    @Override
    public synchronized CompletableFuture<Void> flush() {
        List<AsyncChunk> edited = chunkMap.getCachedCopy();
        CompletableFuture<Void> future = chunkQueue.queueChunks(edited);
        this.chunkMap.clear();
        return future;
    }

    /**
     * Completes the operation synchronously, but fast
     *
     * @return Whether or not the operation completed before the timeout OR FALSE if the current thread is not the server main thread
     */
    @Override
    public synchronized boolean syncFlush(int timeoutMs) {
        if (!Bukkit.isPrimaryThread())
            return false;
        List<AsyncChunk> edited = chunkMap.getCachedCopy();
        edited.removeIf(c -> !c.isEdited());
        List<QueuedChunk> queue = edited.stream().map((c) -> new QueuedChunk(c, null)).collect(Collectors.toList());
        chunkQueue.update(queue, new ReentrantLock(true), timeoutMs);
        this.chunkMap.clear();
        return edited.isEmpty();
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

        int threads = Runtime.getRuntime().availableProcessors();

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
            List<Future<Void>> futures = new ArrayList<>();
            while (it.hasNext() && !(timeout != -1 && System.currentTimeMillis() - ms > timeout)) {
                for (int i = 0; i < threads && it.hasNext(); i++) {
                    AsyncChunk chunk = it.next();
                    chunk.getBukkitChunk().load();
                    futures.add(executor.submit(() -> {
                        chunk.refresh(finalSectionMask);
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
}
