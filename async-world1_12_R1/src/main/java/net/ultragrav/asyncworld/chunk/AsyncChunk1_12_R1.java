package main.java.net.ultragrav.asyncworld.chunk;

import main.java.net.ultragrav.asyncworld.AsyncChunk;
import main.java.net.ultragrav.asyncworld.AsyncWorld;
import main.java.net.ultragrav.asyncworld.ChunkLocation;
import net.minecraft.server.v1_12_R1.*;
import net.ultragrav.utils.Vector3D;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;

import java.util.*;

public class AsyncChunk1_12_R1 extends AsyncChunk {
    public AsyncChunk1_12_R1(AsyncWorld parent, ChunkLocation loc) {
        super(parent, loc);
        Arrays.fill(biomes, (byte) -1);
    }

    private byte[] biomes = new byte[256];

    @Override
    public void setBiome(int x, int z, byte biome) {
        biomes[z << 4 | x] = biome;
    }

    @Override
    public short getCombinedBlockSync(int x, int y, int z) {
        Chunk nmsChunk = getNmsChunk();
        ChunkSection[] sections = nmsChunk.getSections();
        ChunkSection section = sections[y >> 4];
        if (section == null) {
            return 0;
        }
        IBlockData data = section.getType(x, y & 15, z);
        return (short) Block.getCombinedId(data);
    }

    private Map<BlockPosition, TileEntity> tilesToRemove = new HashMap<>();
    private Map<BlockPosition, TileEntity> tilesToAdd = new HashMap<>();

    private Chunk getNmsChunk() {
        ChunkLocation loc = this.getLoc();
        return ((CraftChunk) loc.getWorld().getBukkitWorld().getChunkAt(loc.getX(), loc.getZ())).getHandle();
    }

    @Override
    public void start() {
        getBukkitChunk().load(true);
    }

    @Override
    public void end(int mask) {
        Chunk nmsChunk = getNmsChunk();

        //Remove tile entity
        tilesToRemove.forEach((bp, te) -> {
            nmsChunk.world.s(bp); //Remove it from the world
            nmsChunk.getTileEntities().remove(bp); //Remove it from the chunk
            te.z(); //Got no idea what this does but it's needed
            te.invalidateBlockCache(); //Set tile entity's parent block to null
        });

        tilesToRemove.clear();

        //Add tile entities
        tilesToAdd.forEach((bp, te) -> {
            nmsChunk.getWorld().setTileEntity(bp, te); //Set in world (also sets block position and world of tile entity)
            nmsChunk.getTileEntities().put(bp, te); //Set in chunk
        });

        tilesToAdd.clear();

        this.sendPackets(mask);
    }

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
    public void update() {
        ChunkLocation loc = this.getLoc();
        Chunk nmsChunk = ((CraftChunk) loc.getWorld().getBukkitWorld().getChunkAt(loc.getX(), loc.getZ())).getHandle();

        nmsChunk.mustSave = true;
        nmsChunk.f(true);

        int bx = loc.getX() << 4;
        int bz = loc.getZ() << 4;

        ChunkSection[] sections = nmsChunk.getSections();
        for (int sectionIndex = 0; sectionIndex < 16; sectionIndex++) {
            if ((this.getEditedSections() >> sectionIndex & 1) == 0)
                continue;
            ChunkSection section = sections[sectionIndex];
            if (section == null) {
                section = sections[sectionIndex] = new ChunkSection(sectionIndex << 4, true);
            }

            GUChunkSection guChunkSection = chunkSections[sectionIndex];

            short[] sectionContents = guChunkSection == null ? null : guChunkSection.contents;

            int air = 0;

            for (int i = 0; i < 4096; i++) {

                short block = sectionContents != null ? sectionContents[i] : 0;

                int lx = i >>> 8;
                int ly = i & 15;
                int lz = i >>> 4 & 15;

                if (block == -2)
                    continue;


                if (block == 0) {
                    if (cuboidEdits == null)
                        continue;
                    boolean edit = false;
                    for (CuboidEdit edits : cuboidEdits) {
                        if (edits.getRegion().contains(new Vector3D(lx + bx, ly + (sectionIndex << 4), lz + bz))) {
                            edit = true;
                            block = edits.getBlockSupplier().get();
                            if (block == 0)
                                block = -1;
                        }
                    }
                    if (!edit)
                        continue;
                }
                if (block == -1) {
                    block = 0;
                    air++;
                }

                section.setType(lx, ly, lz, Block.getByCombinedId(block));
                section.getSkyLightArray().a(lx, ly, lz, 15);

                //Remove tile entity
                BlockPosition position = new BlockPosition(lx + bx, ly + (sectionIndex << 4), lz + bz);
                TileEntity te = nmsChunk.getTileEntities().get(position);
                if (te != null)
                    tilesToRemove.put(position, te);
            }
            if (air == 65536) {
                sections[sectionIndex] = null;
            }
        }

        //Biomes
        byte[] chunkBiomes = nmsChunk.getBiomeIndex();
        for (int i = 0; i < chunkBiomes.length && i < biomes.length; i++)
            if (biomes[i] != -1)
                chunkBiomes[i] = biomes[i];
        Arrays.fill(biomes, (byte) -1);

        //heightmap/lighting
        nmsChunk.initLighting();
    }

    @Override
    protected void loadFromChunk(int sectionMask) {
        Chunk chunk = getNmsChunk();
        ChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            if ((sectionIndex >> sectionIndex & 1) == 0)
                continue;

            ChunkSection section = sections[sectionIndex];
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        int block = section != null ? Block.getCombinedId(sections[sectionIndex].getType(x, y, z)) : 0;
                        this.writeBlock(x, y + (sectionIndex << 4), z, block & 4095, (byte) (block >>> 12));
                    }
                }
            }
        }
    }
}