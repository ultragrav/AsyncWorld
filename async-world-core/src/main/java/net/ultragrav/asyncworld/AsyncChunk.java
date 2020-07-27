package net.ultragrav.asyncworld;

import lombok.AccessLevel;
import lombok.Getter;
import net.ultragrav.utils.CuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public abstract class AsyncChunk implements Callable<AsyncChunk> {

    //TODO calculate lighting (we can do this one tmw together)

    protected GUChunkSection[] chunkSections = new GUChunkSection[16];
    protected List<CuboidEdit> cuboidEdits;
    @Getter
    private ChunkLocation loc;
    @Getter(AccessLevel.PROTECTED)
    private int editedSections;
    @Getter
    private AsyncWorld parent;

    public AsyncChunk(AsyncWorld parent, ChunkLocation loc) {
        this.parent = parent;
        this.loc = loc;
    }

    public synchronized void writeBlock(int x, int y, int z, int id, byte data) {
        if (id < 0 && id != -2)
            throw new IllegalArgumentException("ID cannot be less than 0 (air)");
        if (y < 0)
            return;
        if (y > 255)
            return;
        if (id == 0)
            id = -1;

        if (chunkSections[y >>> 4] == null)
            chunkSections[y >>> 4] = new GUChunkSection();

        chunkSections[y >>> 4].contents[x << 8 | z << 4 | y & 15] = (short) (data << 12 | (id > 0 ? id & 4095 : id));
        editedSections |= 1 << (y >>> 4);
    }

    /**
     * Loads the actual chunk's data into this instance
     * NOTE: This is not done on creation so if you need to read blocks, refresh manually
     */
    public synchronized void refresh(int sectionMask) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getLogger().info("Called AsyncChunk.refresh() from asynchronous thread!");
            return;
        }
        this.loadFromChunk(sectionMask);
    }

    public synchronized int readBlock(int x, int y, int z) {
        if (y < 0)
            return -1;
        if (y > 255)
            return -1;
        GUChunkSection section = chunkSections[y >>> 4];
        if (section == null)
            return -1;
        short data = section.contents[x << 8 | z << 4 | y & 15];
        return data == 0 ? -1 : (data == -1 ? 0 : data);
    }

    public synchronized void addCuboidEdit(CuboidEdit edit) {
        if (cuboidEdits == null)
            cuboidEdits = new ArrayList<>();
        cuboidEdits = new ArrayList<>();

        this.cuboidEdits.add(edit);

        int minY = edit.getRegion().getMinimumY() >> 4;
        int maxY = edit.getRegion().getMaximumY() >> 4;
        for (int i = minY; i <= maxY; i++) {
            editedSections |= 1 << i;
        }
    }

    public abstract void setBiome(int x, int z, byte biome);

    public abstract short getCombinedBlockSync(int x, int y, int z);

    /**
     * Must be called sync
     */
    public synchronized AsyncChunk call() {
        if (!isEdited())
            return null;
        this.update();
        this.editedSections = 0;
        this.chunkSections = new GUChunkSection[16];
        this.cuboidEdits = null;
        return this;
    }

    public synchronized void setIgnore(int x, int y, int z) {
        writeBlock(x, y, z, -2, (byte) 0);
    }

    //Both of these are called sync before and after call() and update()
    public abstract void start();

    public abstract void end(int packetMask);

    public synchronized boolean isEdited() {
        return editedSections != 0;
    }

    public Chunk getBukkitChunk() {
        return loc.getWorld().getBukkitWorld().getChunkAt(loc.getX(), loc.getZ());
    }

    public boolean isChunkLoaded() {
        return loc.getWorld().getBukkitWorld().isChunkLoaded(loc.getX(), loc.getZ());
    }

    protected abstract void update();

    protected abstract void loadFromChunk(int sectionMask);

    protected static class GUChunkSection {
        public short[] contents = new short[4096];
    }

    public static class CuboidEdit {
        private CuboidRegion region;
        private Supplier<Short> blockSupplier;

        public CuboidEdit(CuboidRegion region, Supplier<Short> blockSupplier) {
            this.region = region;
            this.blockSupplier = blockSupplier;
        }

        public CuboidRegion getRegion() {
            return this.region;
        }

        public Supplier<Short> getBlockSupplier() {
            return this.blockSupplier;
        }

    }
}
