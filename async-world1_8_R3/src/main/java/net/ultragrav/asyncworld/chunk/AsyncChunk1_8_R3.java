package main.java.net.ultragrav.asyncworld.chunk;

import com.soraxus.prisons.util.world.Vector;
import main.java.net.ultragrav.asyncworld.AsyncChunk;
import main.java.net.ultragrav.asyncworld.AsyncWorld;
import main.java.net.ultragrav.asyncworld.ChunkLocation;


import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.CraftChunk;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;


import java.util.*;


import java.util.HashMap;

public class AsyncChunk1_8_R3 extends AsyncChunk {
    public AsyncChunk1_8_R3(AsyncWorld parent, ChunkLocation loc) {
        super(parent, loc);
        Arrays.fill(biomes, (byte) -1);
    }

    private byte[] biomes = new byte[256];

    @Override
    public void setBiome(int x, int z, byte biome) {
        biomes[z << 4 | x] = biome;
    }

    private boolean loaded;
    private Map<BlockPosition, TileEntity> tilesToRemove = new HashMap<>();
    private Map<BlockPosition, TileEntity> tilesToAdd = new HashMap<>();

    private net.minecraft.server.v1_8_R3.Chunk getNmsChunk() {
        ChunkLocation loc = this.getLoc();
        return ((CraftChunk) Bukkit.loc.getWorld().getBukkitWorld().getChunkAt(loc.getX(), loc.getZ())).getHandle();
        //return null;
    }

    @Override
    public short getCombinedBlockSync(int x, int y, int z) {
        net.minecraft.server.v1_8_R3.Chunk nmsChunk = getNmsChunk();
        if(nmsChunk.getSections()[y >> 4] == null)
            return 0;
        IBlockData data = nmsChunk.getSections()[y >> 4].getType(x, y & 15, z);
        return (short) Block.getCombinedId(data);
    }

    @Override
    public void start() {
//        loaded = getLoc().getWorld().getBukkitWorld().isChunkLoaded(getLoc().getX(), getLoc().getZ());
//        if(!loaded)
//            getBukkitChunk().load(true);
    }

    @Override
    public void end(int mask) {
        net.minecraft.server.v1_8_R3.Chunk nmsChunk = getNmsChunk();

        //Remove tile entity
        tilesToRemove.forEach((bp, te) -> {
            nmsChunk.getWorld().t(bp); //Remove it from the world
            nmsChunk.getTileEntities().remove(bp); //Remove it from the chunk
            te.y(); //Got no idea what this does but it's needed
            te.E(); //Set tile entity's parent block to null
        });

        tilesToRemove.clear();

        tilesToAdd.forEach((bp, te) -> {
            nmsChunk.getWorld().setTileEntity(bp, te); //Set in world (also sets block position and world of tile entity)
            nmsChunk.getTileEntities().put(bp, te); //Set in chunk
        });

        tilesToAdd.clear();

        this.sendPackets(mask);

        if(!loaded)
            getBukkitChunk().unload();
    }

    public void sendPackets(int mask) {
        ChunkLocation loc = this.getLoc();
        net.minecraft.server.v1_8_R3.Chunk nmsChunk = ((CraftChunk) loc.getWorld().getBukkitWorld().getChunkAt(loc.getX(), loc.getZ())).getHandle();
        PacketPlayOutMapChunk packet;
        PacketPlayOutMapChunk secondMapPacket;

        //The client will for some reason de-spawn entities in map chunk updates which have a mask
        // of 65535 or 0 however 0 will never be called so only check for 65535
        if(mask == 65535) {
            packet = new net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk(nmsChunk,false,65280);
            secondMapPacket = new net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk(nmsChunk,false,255);
        } else {
            packet = new net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk(nmsChunk,false, mask);
            secondMapPacket = null;
        }

        List<net.minecraft.server.v1_8_R3.Packet> tilePackets = new ArrayList<>();
        nmsChunk.getTileEntities().forEach((key, value) -> tilePackets.add(value.getUpdatePacket()));


        PlayerChunkMap map = ((WorldServer) nmsChunk.getWorld()).getPlayerChunkMap();
        for (Player player : Bukkit.getOnlinePlayers()) {
            EntityPlayer pl = ((CraftPlayer) player).getHandle();
            if (map.a(pl, loc.getX(), loc.getZ())) {
                pl.playerConnection.sendPacket(packet);
                if(secondMapPacket != null) {
                    pl.playerConnection.sendPacket(secondMapPacket);
                }
                tilePackets.forEach(pack -> pl.playerConnection.sendPacket(packet));
            }
        }
    }

    public void update() {

        ChunkLocation loc = this.getLoc();
        net.minecraft.server.v1_8_R3.Chunk nmsChunk = ((CraftChunk) loc.getWorld().getBukkitWorld().getChunkAt(loc.getX(), loc.getZ())).getHandle();

        nmsChunk.mustSave = true;
        nmsChunk.f(true);

        int bx = loc.getX() << 4;
        int bz = loc.getZ() << 4;

        ChunkSection[] sections = nmsChunk.getSections();
        for (int sectionIndex = 0; sectionIndex < 16; sectionIndex++) {
            if((this.getEditedSections() >> sectionIndex & 1) == 0)
                continue;
            ChunkSection section = sections[sectionIndex];
            if (section == null) {
                section = sections[sectionIndex] = new ChunkSection(sectionIndex << 4, true);
            }

            GUChunkSection guChunkSection = chunkSections[sectionIndex];

            short[] sectionContents = guChunkSection == null ? null : guChunkSection.contents;

            int air = 0;

            for(int i = 0; i < 4096; i++) {

                int block = sectionContents != null ? sectionContents[i] : 0;

                int lx = i >>> 8;
                int ly = i & 15;
                int lz = i >>> 4 & 15;

                if(block == -2) //ignore
                  continue;

                if (block == 0) {
                    if(cuboidEdits == null)
                        continue;
                    boolean edit = false;
                    for (CuboidEdit edits : cuboidEdits) {
                        if (edits.getRegion().contains(new Vector(lx + bx, ly + (sectionIndex << 4), lz + bz))) {
                            edit = true;
                            block = (int) edits.getBlockSupplier().get();
                            if(block == 0)
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

                section.setType(lx, ly, lz, net.minecraft.server.v1_8_R3.Block.getByCombinedId(block));
                ////section.getSkyLightArray().a(lx, ly, lz, 15);

                //Remove tile entity
                BlockPosition position = new BlockPosition(lx + bx, ly + (sectionIndex << 4), lz + bz);
                TileEntity te = nmsChunk.getTileEntities().get(position);
                if (te != null)
                    tilesToRemove.put(position, te);
            }
            if(air == 65536) {
                sections[sectionIndex] = null;
            }
        }

        //Biomes
        byte[] chunkBiomes = nmsChunk.getBiomeIndex();
        for (int i = 0; i < chunkBiomes.length && i < biomes.length; i++)
            if (biomes[i] != -1)
                chunkBiomes[i] = biomes[i];
        Arrays.fill(biomes, (byte) -1);

        nmsChunk.initLighting();
    }

    @Override
    protected void loadFromChunk(int sectionMask) {
        net.minecraft.server.v1_8_R3.Chunk chunk = getNmsChunk();
        ChunkSection[] sections = chunk.getSections();
        for(int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            for(int x = 0; x < 16; x++){
                for(int y = 0; y < 16; y++) {
                    for(int z = 0; z < 16; z++) {
                        int block = section != null ? Block.getCombinedId(sections[sectionIndex].getType(x, y, z)) : 0;
                        this.writeBlock(x, y + (sectionIndex << 4), z, block & 4095, (byte) (block >>> 12));
                    }
                }
            }
        }
    }
}
