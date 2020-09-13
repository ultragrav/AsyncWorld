package net.ultragrav.asyncworld.customworld;

import net.minecraft.server.v1_12_R1.*;
import net.ultragrav.asyncworld.AsyncWorld;
import net.ultragrav.asyncworld.ChunkLocation;
import net.ultragrav.asyncworld.chunk.AsyncChunk1_12_R1;
import net.ultragrav.asyncworld.nbt.TagCompound;
import net.ultragrav.asyncworld.nbt.TagInt;

import java.util.Arrays;

public class CustomWorldAsyncChunk1_12 extends CustomWorldAsyncChunk<WorldServer> {
    public CustomWorldAsyncChunk1_12(AsyncWorld parent, ChunkLocation loc) {
        super(parent, loc);
        Arrays.fill(biomes, (byte) 1);
    }

    private Chunk nmsStoredChunk;
    private volatile boolean finished = false;
    private final ChunkSection[] sections = new ChunkSection[16];

    @Override
    public synchronized void setEmittedLight(int x, int y, int z, int value) {
        int sectionIndex = y >> 4;
        ChunkSection section = sections[sectionIndex];
        if (section == null) {
            section = sections[sectionIndex] = new ChunkSection(sectionIndex << 4, true);
        }
        section.getEmittedLightArray().a(x, y & 0xF, z, value & 0xF);
    }

    @Override
    public void sendPackets(int mask) {

    }

    @Override
    public synchronized void setBlock(int sectionIndex, int index, int block, boolean addTile) {
        ChunkSection section = sections[sectionIndex];
        if (section == null) {
            section = sections[sectionIndex] = new ChunkSection(sectionIndex << 4, true);
        }
        int x = getLX(index), y = getLY(index), z = getLZ(index);
        section.getSkyLightArray().a(x, y, z, 15);
        section.getBlocks().setBlock(x, y, z, Block.getByCombinedId(block));
        if (addTile && hasTileEntity(block & 0xFFF)) {
            setTileEntity(x, y + (sectionIndex << 4), z, new TagCompound());
        }
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

    public synchronized void finish(WorldServer server) {
        nmsStoredChunk = new Chunk(server, this.getLoc().getX(), this.getLoc().getZ());
        Chunk nmsChunk = nmsStoredChunk;
        nmsChunk.mustSave = true;
        nmsChunk.f(true);

        try {

            for (ChunkSection section : sections) {
                if (section == null)
                    continue;
                section.recalcBlockCounts();
            }

            nmsChunk.a(sections); //Set the blocks

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
                    entity = !block.isTileEntity() ? null : ((ITileEntity)block).a(nmsChunk.getWorld(), iblockdata.getBlock().toLegacyData(iblockdata));
                    if(entity != null) {
                        entity.a(nmsChunk.getWorld()); //Set world
                        nmsChunk.getWorld().a(entity); //Add to world
                        nmsChunk.a(bp, entity); //Add to chunk
                    }
                }
                if (entity != null) {

                    //Set Tile Entity's Coordinates in it's NBT
                    te.getData().put("x", new TagInt(bp.getX()));
                    te.getData().put("y", new TagInt(bp.getY()));
                    te.getData().put("z", new TagInt(bp.getZ()));

                    entity.load(AsyncChunk1_12_R1.fromGenericCompound(te)); //Load NBT into tile entity
                }
            });

            this.flushBiomes();
            nmsChunk.initLighting();
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
