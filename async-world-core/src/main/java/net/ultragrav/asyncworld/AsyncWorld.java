package net.ultragrav.asyncworld;

import net.ultragrav.asyncworld.nbt.TagCompound;
import net.ultragrav.asyncworld.schematics.Schematic;
import net.ultragrav.utils.CuboidRegion;
import net.ultragrav.utils.IntVector3D;
import net.ultragrav.utils.Vector3D;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public abstract class AsyncWorld {

    public interface AsyncWorldTriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }

    public interface AsyncWorldQuadConsumer<D, I, C, K> {
        void accept(D d, I i, C c, K k);
    }

    public abstract World getBukkitWorld();

    public abstract AsyncChunk getChunk(int cx, int cz);

    protected abstract AsyncChunk getNewChunk(int cx, int cz);

    public abstract int syncGetBlock(int x, int y, int z);

    public abstract int getCachedBlock(int x, int y, int z);

    public abstract void pasteSchematic(Schematic schematic, IntVector3D position);

    public abstract void setBlocks(CuboidRegion region, Supplier<Short> blockSupplier);

    public abstract void syncForAllInRegion(CuboidRegion region, BiConsumer<Vector3D, Integer> action, boolean multiThread);

    /**
     * Perform a synchronous action on all blocks in a region with parameters of block position, block combined id/data, and TileEntity NBT Data (null if none)
     */
    public abstract void syncForAllInRegion(CuboidRegion region, AsyncWorldTriConsumer<Vector3D, Integer, TagCompound> action, boolean multiThread);

    /**
     * Perform an asynchronous action on all blocks in a region. NOTE: This makes use of syncFastRefreshChunksInRegion and must wait
     * for the next tick if not called synchronously, in this case it will take at least 50ms to execute
     */
    public abstract void asyncForAllInRegion(CuboidRegion region, AsyncWorldQuadConsumer<Vector3D, Integer, TagCompound, Integer> action, boolean multiThread);

    /**
     * If anyone reading this at any point DOES NOT know what a Tile Entity is, here is an explanation <br></br>
     * Tile Entities are sort of "entities" that have a fixed BLOCK POSITION (only ints) and don't move, they are <br></br>
     * basically just NBTTagCompounds assigned to certain block positions. Chests have tile entities (actually now called block entities) <br></br>
     * that contain data about the chest such as customName, containedItems, position, etc. Item frames have data on what item is contained and in what rotation etc. <br></br>
     * For more information visit the gamepedia page on Block Entities
     */
    public abstract void setTile(int x, int y, int z, TagCompound tag);

    public abstract void setBlock(int x, int y, int z, int id, byte data);

    public abstract void setIgnore(int x, int y, int z);

    public abstract CompletableFuture<Void> flush();

    public abstract boolean syncFlush(int timeoutMs);

    public abstract void syncFastRefreshChunksInRegion(CuboidRegion region, int timeout);
}
