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

    public abstract void setTile(int x, int y, int z, TagCompound tag);

    public abstract void setBlock(int x, int y, int z, int id, byte data);

    public abstract void setIgnore(int x, int y, int z);

    public abstract CompletableFuture<Void> flush();

    public abstract boolean syncFlush(int timeoutMs);

    public abstract void syncFastRefreshChunksInRegion(CuboidRegion region, int timeout);
}
