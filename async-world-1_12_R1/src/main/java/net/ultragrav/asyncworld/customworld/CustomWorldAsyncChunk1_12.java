package net.ultragrav.asyncworld.customworld;

import net.minecraft.server.v1_12_R1.*;
import net.ultragrav.asyncworld.AsyncWorld;
import net.ultragrav.asyncworld.ChunkLocation;
import net.ultragrav.asyncworld.chunk.AsyncChunk1_12_R1;
import net.ultragrav.asyncworld.chunk.NextTickEntry;
import net.ultragrav.nbt.wrapper.*;
import net.ultragrav.utils.IntVector3D;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;

import java.util.*;

public class CustomWorldAsyncChunk1_12 extends CustomWorldAsyncChunk<WorldServer> {

    private static final byte[] maxFilledSkyLight = new byte[2048];

    static {
        Arrays.fill(maxFilledSkyLight, (byte) 0xFF);
    }

    private CustomWorldAsyncWorld parent;

    public CustomWorldAsyncChunk1_12(CustomWorldAsyncWorld parent, ChunkLocation loc) {
        super(parent, loc);
        this.parent = parent;
        Arrays.fill(biomes, (byte) 1);
    }

    private Chunk nmsStoredChunk;
    private volatile boolean finished = false;
    private final ChunkSection[] sections = new ChunkSection[16];
    private final int[] nonAirCounts = new int[16];
    private final int[] heightMap = new int[256];
    private boolean dirtyHeightMap = false;
    private CustomWorldChunkSnap cachedSnap = null;


    private boolean isNormalWorld() {
        return parent.getCustomWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL;
    }

    @Override
    public synchronized void setEmittedLight(int x, int y, int z, int value) {
        int sectionIndex = y >> 4;
        ChunkSection section = sections[sectionIndex];
        if (section == null) {
            section = sections[sectionIndex] = new ChunkSection(sectionIndex << 4, isNormalWorld());
        }
        section.getEmittedLightArray().a(x, y & 0xF, z, value & 0xF);
    }

    @Override
    public void sendPackets(int mask) {

        ChunkLocation loc = this.getLoc();
        Chunk nmsChunk = ((CraftChunk) loc.getWorld().getBukkitWorld().getChunkAt(loc.getX(), loc.getZ())).getHandle();
        PacketPlayOutMapChunk packet;
        PacketPlayOutMapChunk secondMapPacket;

        //The client will for some reason de-spawn entities in map chunk updates which have a mask
        // of 65535 or 0 however 0 will never be called so only check for 65535
        if (mask == 65535) {
            packet = new PacketPlayOutMapChunk(nmsChunk, 65280);
            secondMapPacket = new PacketPlayOutMapChunk(nmsChunk, 255);
        } else {
            packet = new PacketPlayOutMapChunk(nmsChunk, mask);
            secondMapPacket = null;
        }

        List<Packet<?>> tilePackets = new ArrayList<>();
        nmsChunk.getTileEntities().forEach((key, value) -> tilePackets.add(value.getUpdatePacket()));

        PlayerChunkMap map = ((WorldServer) nmsChunk.getWorld()).getPlayerChunkMap();
        PlayerChunk playerChunk = map.getChunk(loc.getX(), loc.getZ());
        if (playerChunk == null)
            return;
        playerChunk.c.forEach(p -> {
            p.playerConnection.sendPacket(packet);
            if (secondMapPacket != null) {
                p.playerConnection.sendPacket(secondMapPacket);
            }
            tilePackets.forEach(packet1 -> p.playerConnection.sendPacket(packet1));
        });
    }

    @Override
    public synchronized void setBlock(int x, int y, int z, int block, boolean addTile) {
        int sectionIndex = y >> 4;
        ChunkSection section = sections[sectionIndex];
        if (section == null) {
            if (block == 0)
                return;
            section = sections[sectionIndex] = new ChunkSection(sectionIndex << 4, isNormalWorld());
        }

        IBlockData blockData = Block.getByCombinedId(block);
        section.getBlocks().setBlock(x, y & 15, z, blockData);

        //Block counts
        if (block != 0) {
            nonAirCounts[sectionIndex]++;
        }

        //Height map
        if (!dirtyHeightMap && blockData.c() != 0) {
            int current = heightMap[z << 4 | x];
            if (y > current)
                heightMap[z << 4 | x] = y;
        } else if (!dirtyHeightMap) {
            if (y == heightMap[x << 4 | x])
                dirtyHeightMap = true;
        }

        this.editedSections |= 1 << sectionIndex;
        if (addTile && hasTileEntity(block & 0xFFF)) {
            setTileEntity(x, y + (sectionIndex << 4), z, new TagCompound());
        }
    }

    @Override
    public boolean sectionExists(int section) {
        if (getNmsChunk() == null)
            return false;
        return getNmsChunk().getSections()[section] != null;
    }

    public synchronized void setBiome(int biome) {
        Arrays.fill(biomes, (byte) biome);
        this.flushBiomes();
    }

    public synchronized void setBiome(int x, int z, int biome) {
        this.biomes[z << 4 | x] = (byte) (biome & 0xFF);
    }

    public synchronized void flushBiomes() {
        nmsStoredChunk.a(biomes);
    }

    public synchronized void awaitFinish() {
        if (!this.finished) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized void fromSnap2(Chunk chunk, CustomWorldChunkSnap snap) {

        chunk.e(true);
        chunk.d(true);

        this.biomes = snap.getBiomes(); //Biomes
        snap.getTiles().forEach(t -> { //Tiles
            TileEntity tile = TileEntity.create(chunk.getWorld(), AsyncChunk1_12_R1.fromGenericCompound(t));
            if (tile != null)
                chunk.a(tile);
        });

        //Entities
        snap.getEntities().forEach(e -> loadEntity(e, chunk.getWorld(), chunk));

        snap.getNextTickEntries().forEach(t -> chunk.getWorld().b(new BlockPosition(t.getX(), t.getY(), t.getZ()), Block.getByName(t.getBlockId()), (int) t.getDelay(), t.getPriority()));
    }

    private Entity loadEntity(TagCompound tag, World world, Chunk chunk) {
        Entity entity = EntityTypes.a(AsyncChunk1_12_R1.fromGenericCompound(tag), world);
        chunk.g(true); //Literally have no idea what this does - something to do with saving

        if (entity != null) {

            chunk.a(entity);

            Map<String, Tag<?>> map = tag.getData();

            if (map.containsKey("Passengers")) {
                List<Tag<?>> passengersList = ((TagList) map.get("Passengers")).getData();

                for (Tag<?> passengerTag : passengersList) {
                    Entity passenger = loadEntity((TagCompound) passengerTag, world, chunk);

                    if (passengerTag != null) {
                        passenger.a(entity, true);
                    }
                }
            }
        }

        return entity;
    }

    @Override
    public synchronized void fromSnap(CustomWorldChunkSnap snap) {
        ChunkSection[] sects;
        if (nmsStoredChunk != null) {
            //Replace existing
            nmsStoredChunk = new Chunk(nmsStoredChunk.world, this.getLoc().getX(), this.getLoc().getZ());
            sects = nmsStoredChunk.getSections();
            fromSnap2(nmsStoredChunk, snap);
        } else {
            //Cache
            cachedSnap = snap;
            sects = sections;
        }

        //Set lighting (sky, emitted), blocks, and height map
        byte[][] blocks = CustomWorldChunkSnap.getBlocksInternal(snap); //NMS copies this so we don't have to.
        byte[][] blockData = CustomWorldChunkSnap.getBlockDataInternal(snap); //^
        byte[][] skyLight = snap.getSkyLight(); //NMS does not copy this, so we have to.
        byte[][] emittedLight = snap.getEmittedLight(); //^

        short mask = snap.getSectionBitMask();
        for (int i = 0; i < 16; i++) {
            if (((mask >>> i) & 1) == 0)
                continue;

            ChunkSection section = sects[i] = new ChunkSection(i << 4, isNormalWorld());

            if (skyLight[i] != null) {
                section.b(new NibbleArray(skyLight[i])); //Sky light
            }
            section.a(new NibbleArray(emittedLight[i])); //Emitted light

            section.getBlocks().a(blocks[i], new NibbleArray(blockData[i]), null); //Blocks
        }

        System.arraycopy(snap.getHeightMap(), 0, heightMap, 0, heightMap.length); //Height map
    }

    public synchronized void finish(WorldServer server) {
        try {
            nmsStoredChunk = new Chunk(server, this.getLoc().getX(), this.getLoc().getZ());
            Chunk nmsChunk = nmsStoredChunk;
            nmsChunk.mustSave = true;
            nmsChunk.f(true);

            nmsChunk.a(sections); //Set the blocks

            //Block counts
            if (this.cachedSnap != null) {
                //Snap
                fromSnap2(nmsStoredChunk, cachedSnap);

                //Block counts
                for (ChunkSection section : sections) {
                    if (section == null)
                        continue;
                    section.recalcBlockCounts();
                }
            } else {

                //Block counts
                for (int i = 0, sectionsLength = sections.length; i < sectionsLength; i++) {
                    ChunkSection section = sections[i];
                    if (section == null)
                        continue;

                    //Default skylighting
                    if (section.getSkyLightArray() != null) {
                        System.arraycopy(maxFilledSkyLight, 0, section.getSkyLightArray().asBytes(), 0, 2048);
                    }

                    try {
                        AsyncChunk1_12_R1.setCount(0, nonAirCounts[i], section);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            //Heightmap
            if (!dirtyHeightMap) {
                System.arraycopy(heightMap, 0, nmsStoredChunk.heightMap, 0, nmsStoredChunk.heightMap.length);
            } else {
                nmsStoredChunk.initLighting();
            }

            //Tile Entities
            getTiles().forEach((intVector3D, te) -> {
                BlockPosition bp = new BlockPosition(intVector3D.getX(), intVector3D.getY(), intVector3D.getZ());
                TileEntity entity;
                synchronized (server) {

                    //All of this is to prevent the world from calling a chunk load when adding the tile to the world
                    //Otherwise we'd use world.getTileEntity(bp) which does all of this for us but also calls a chunk load
                    //Which spigot catches as asynchronous and blocks until main thread catches up, causing a crash
                    IBlockData iblockdata = nmsChunk.getBlockData(bp);
                    Block block = iblockdata.getBlock();
                    entity = !block.isTileEntity() ? null : ((ITileEntity) block).a(nmsChunk.getWorld(), iblockdata.getBlock().toLegacyData(iblockdata));
                    if (entity != null) {

                        //Set Tile Entity's Coordinates in it's NBT
                        te.getData().put("x", new TagInt(bp.getX()));
                        te.getData().put("y", new TagInt(bp.getY()));
                        te.getData().put("z", new TagInt(bp.getZ()));

                        entity.load(AsyncChunk1_12_R1.fromGenericCompound(te)); //Load NBT into tile entity

                        nmsChunk.getWorld().a(entity); //Add to world
                        nmsChunk.a(bp, entity); //Add to chunk
                        entity.a(nmsChunk.getWorld()); //Set world
                    }
                }
            });

            this.flushBiomes();

            if (getEditedSections() != 0) //No blocks edited, or loaded from chunk snap -> which contains lighting already
                nmsChunk.initLighting();

            this.cachedSnap = null;
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        } finally {
            finished = true;
            this.notifyAll();
        }
    }

    public synchronized Chunk getStoredChunk() {
        return nmsStoredChunk;
    }

    @Override
    public int getCombinedBlockSync(int x, int y, int z) {
        return 0;
    }

    @Override
    public void setCombinedBlockSync(int x, int y, int z, int combinedBlock) {
    }

    @Override
    protected void optimizeSection(int i, GUChunkSection guChunkSection) {

    }

    @Override
    public void start() {
    }

    @Override
    public void end(int mask) {
    }

    @Override
    public int syncGetEmittedLight(int x, int y, int z) {
        return 0;
    }

    @Override
    public int syncGetSkyLight(int x, int y, int z) {
        return 0;
    }

    @Override
    public int syncGetBrightnessOpacity(int x, int y, int z) {
        return 0;
    }

    @Override
    public void syncSetEmittedLight(int x, int y, int z, int value) {

    }

    @Override
    public void syncSetSkyLight(int x, int y, int z, int value) {

    }

    public Chunk getNmsChunk() {
        this.awaitFinish();
        return this.nmsStoredChunk;
    }

    @Override
    public Map<IntVector3D, TagCompound> syncGetTiles() {
        Map<IntVector3D, TagCompound> list = new HashMap<>();

        getNmsChunk().getTileEntities().forEach((p, t) -> {
            if (t == null)
                return;
            list.put(new IntVector3D(p.getX(), p.getY(), p.getZ()), AsyncChunk1_12_R1.fromNMSCompound(t.save(new NBTTagCompound())));
        });
        return list;
    }

    @Override //Not async safe
    public List<TagCompound> syncGetEntities() {
        List<TagCompound> out = new ArrayList<>();
        for (int i = 0; i < getNmsChunk().getEntitySlices().length; i++) {
            if (getNmsChunk().getEntitySlices()[i] == null)
                continue;
            for (Entity entity : getNmsChunk().getEntitySlices()[i]) {
                //All entities in the i-th section
                if (entity.dead) continue;
                NBTTagCompound nmsCompound = new NBTTagCompound();
                if (entity.d(nmsCompound)) {
                    TagCompound compound = AsyncChunk1_12_R1.fromNMSCompound(nmsCompound);
                    out.add(compound);
                }
            }
        }
        return out;
    }

    @Override
    public int[] syncGetHeightMap() {
        Chunk chunk = getNmsChunk();

        int[] arr = new int[chunk.heightMap.length];
        System.arraycopy(chunk.heightMap, 0, arr, 0, arr.length);
        return arr;
    }

    @Override
    public void syncGetBlocksAndData(byte[] blocks, byte[] data, int section) {
        if (data.length < 2048 || blocks.length < 4096) return;

        ChunkSection sect = getNmsChunk().getSections()[section];
        if (sect == null)
            return;

        sect.getBlocks().exportData(blocks, new NibbleArray(data));
    }

    @Override
    public byte[] syncGetEmittedLight(int section) {
        Chunk chunk = getNmsChunk();
        ChunkSection sect = chunk.getSections()[section];
        if (sect == null)
            return null;
        byte[] arr = new byte[2048];
        System.arraycopy(chunk.getSections()[section].getEmittedLightArray().asBytes(), 0, arr, 0, arr.length);
        return arr;
    }

    @Override
    public byte[] syncGetSkyLight(int section) {
        Chunk chunk = getNmsChunk();
        ChunkSection sect = chunk.getSections()[section];
        if (sect == null)
            return null;

        NibbleArray nibble = sect.getSkyLightArray();
        if (nibble == null)
            return null;

        byte[] arr = new byte[2048];
        System.arraycopy(chunk.getSections()[section].getSkyLightArray().asBytes(), 0, arr, 0, arr.length);
        return arr;
    }

    @Override
    public NextTickEntry[] syncGetNextTickEntries() {
        Chunk chunk = getNmsChunk();

        List<NextTickListEntry> list = chunk.world.a(chunk, false);

        if (list == null) return new NextTickEntry[0];

        NextTickEntry[] entries = new NextTickEntry[list.size()];
        for (int i = 0; i < list.size(); i++) {
            NextTickListEntry entry = list.get(i);
            entries[i] = new NextTickEntry(
                    Block.REGISTRY.b(entry.a()).toString(),
                    entry.a.getX(),
                    entry.a.getY(),
                    entry.a.getZ(),
                    entry.b - chunk.world.getTime(),
                    entry.c
            );
        }

        return entries;
    }

    @Override
    public short getSectionBitMask() {
        ChunkSection[] sections = getNmsChunk().getSections();
        short mask = 0;
        for (int i = 0; i < 16; i++) {
            if (sections[i] != null) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    @Override
    public byte[] syncGetBiomes() {
        Chunk chunk = getNmsChunk();
        byte[] arr = new byte[chunk.getBiomeIndex().length];
        System.arraycopy(chunk.getBiomeIndex(), 0, arr, 0, arr.length);
        return arr;
    }

    @Override
    public void loadTiles() {

    }

    @Override
    protected void update() {

    }

    @Override
    protected void loadFromChunk(int sectionMask) {

    }
}
