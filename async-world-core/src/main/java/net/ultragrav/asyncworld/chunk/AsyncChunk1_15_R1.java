//package main.java.net.ultragrav.asyncworld.chunk;//package com.soraxus.prisons.util.world.chunk;
//
//import com.soraxus.prisons.util.world.AsyncChunk;
//import com.soraxus.prisons.util.world.AsyncWorld;
//import com.soraxus.prisons.util.world.ChunkLocation;
//import com.soraxus.prisons.util.world.Vector;
//import net.minecraft.server.v1_15_R1.*;
//import org.bukkit.Bukkit;
//import org.bukkit.craftbukkit.v1_15_R1.CraftChunk;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public class AsyncChunk1_15_R1 extends AsyncChunk {
//    public AsyncChunk1_15_R1(AsyncWorld parent, ChunkLocation loc) {
//        super(parent, loc);
//    }
//
//    @Override
//    public short getCombinedBlockSync(int x, int y, int z) {
//        if(!Bukkit.isPrimaryThread()) {
//            Bukkit.getLogger().info("Called getCombinedBlockSync from asynchronous thread!");
//            return -1;
//        }
//        net.minecraft.server.v1_15_R1.Chunk nmsChunk = getNmsChunk();
//        IBlockData data = nmsChunk.getSections()[y >> 4].getType(x, y & 15, z);
//        return (short) Block.getCombinedId(data);
//    }
//
//    private boolean loaded;
//    private Map<BlockPosition, TileEntity> tilesToRemove = new HashMap<>();
//
//    private net.minecraft.server.v1_15_R1.Chunk getNmsChunk() {
//        ChunkLocation loc = this.getLoc();
//        return ((org.bukkit.craftbukkit.v1_15_R1.CraftChunk) loc.getWorld().getBukkitWorld().getChunkAt(loc.getX(), loc.getZ())).getHandle();
//    }
//
//    @Override
//    public void start() {
//        loaded = getLoc().getWorld().getBukkitWorld().isChunkLoaded(getLoc().getX(), getLoc().getZ());
//        if (!loaded)
//            getBukkitChunk().load(true);
//    }
//
//    @Override
//    public void end(int mask) {
//        net.minecraft.server.v1_15_R1.Chunk nmsChunk = getNmsChunk();
//
//        //Remove tile entity
//        tilesToRemove.forEach((bp, te) -> {
//            nmsChunk.world.s(bp); //Remove it from the world
//            nmsChunk.getTileEntities().remove(bp); //Remove it from the chunk
//            te.ab_(); //Got no idea what this does but it's needed
//            te.invalidateBlockCache(); //Set tile entity's parent block to null
//        });
//
//        tilesToRemove.clear();
//
//        this.sendPackets(mask);
//
//        if (!loaded)
//            getBukkitChunk().unload();
//    }
//
//    public void sendPackets(int mask) {
//
//        ChunkLocation loc = this.getLoc();
//        net.minecraft.server.v1_15_R1.Chunk nmsChunk = ((CraftChunk) loc.getWorld().getBukkitWorld().getChunkAt(loc.getX(), loc.getZ())).getHandle();
//
//        net.minecraft.server.v1_15_R1.PacketPlayOutMapChunk packet;
//        net.minecraft.server.v1_15_R1.PacketPlayOutMapChunk secondMapPacket;
//
//        //The client will for some reason de-spawn entities in map chunk updates which have a mask
//        // of 65535 or 0 however 0 will never be called so only check for 65535
//        if (mask == 65535) {
//            packet = new net.minecraft.server.v1_15_R1.PacketPlayOutMapChunk(nmsChunk, 65280);
//            secondMapPacket = new net.minecraft.server.v1_15_R1.PacketPlayOutMapChunk(nmsChunk, 255);
//        } else {
//            packet = new net.minecraft.server.v1_15_R1.PacketPlayOutMapChunk(nmsChunk, mask);
//            secondMapPacket = null;
//        }
//
//        List<net.minecraft.server.v1_15_R1.Packet> tilePackets = new ArrayList<>();
//        nmsChunk.getTileEntities().forEach((key, value) -> tilePackets.add(value.getUpdatePacket()));
//
//        net.minecraft.server.v1_15_R1.PlayerChunkMap map = ((WorldServer) nmsChunk.getWorld()).getChunkProvider().playerChunkMap;
//        net.minecraft.server.v1_15_R1.PlayerChunk playerChunk = map.visibleChunks.get((long) loc.getX() << 32 | loc.getZ());
//        if (playerChunk == null)
//            return;
//        playerChunk.players.a(new ChunkCoordIntPair(loc.getX(), loc.getZ()), false).forEach(p -> {
//            p.playerConnection.sendPacket(packet);
//            if (secondMapPacket != null) {
//                p.playerConnection.sendPacket(secondMapPacket);
//            }
//            tilePackets.forEach(packet1 -> p.playerConnection.sendPacket(packet1));
//        });
//
//    }
//
//    @Override
//    public void update() {
//        ChunkLocation loc = this.getLoc();
//        net.minecraft.server.v1_15_R1.Chunk nmsChunk = ((CraftChunk) loc.getWorld().getBukkitWorld().getChunkAt(loc.getX(), loc.getZ())).getHandle();
//
//        nmsChunk.mustNotSave = false;
//        nmsChunk.setNeedsSaving(true);
//
//        int bx = loc.getX() << 4;
//        int bz = loc.getZ() << 4;
//
//        net.minecraft.server.v1_15_R1.ChunkSection[] sections = nmsChunk.getSections();
//        for (int sectionIndex = 0; sectionIndex < 16; sectionIndex++) {
//            if ((this.getEditedSections() >> sectionIndex & 1) == 0)
//                continue;
//            net.minecraft.server.v1_15_R1.ChunkSection section = sections[sectionIndex];
//            if (section == null) {
//                section = sections[sectionIndex] = new net.minecraft.server.v1_15_R1.ChunkSection(sectionIndex << 4);
//            }
//
//            GUChunkSection guChunkSection = chunkSections[sectionIndex];
//
//            short[] sectionContents = guChunkSection == null ? null : guChunkSection.contents;
//
//            int air = 0;
//
//            for(int i = 0; i < 4096; i++) {
//
//                int block = sectionContents != null ? sectionContents[i] : 0;
//
//                int lx = i >>> 8;
//                int ly = i & 15;
//                int lz = i >>> 4 & 15;

//                if(block == -2) //ignore
//                    continue;
//
//                if (block == 0) {
//                    if(cuboidEdits == null)
//                        continue;
//                    boolean edit = false;
//                    for (CuboidEdit edits : cuboidEdits) {
//                        if (edits.getRegion().contains(new Vector(lx + bx, ly + (sectionIndex << 4), lz + bz))) {
//                            edit = true;
//                            block = (int) edits.getBlockSupplier().get();
//                            if(block == 0)
//                                block = -1;
//                        }
//                    }
//                    if (!edit)
//                        continue;
//                }
//
//                if (block == -1) {
//                    block = 0;
//                    air++;
//                }
//
//                section.setType(lx, ly, lz, net.minecraft.server.v1_15_R1.Block.getByCombinedId(block));
//
//
//                //Remove tile entity
//                net.minecraft.server.v1_15_R1.BlockPosition position = new net.minecraft.server.v1_15_R1.BlockPosition(lx + bx, ly + (sectionIndex << 4), lz + bz);
//                net.minecraft.server.v1_15_R1.TileEntity te = nmsChunk.getTileEntities().get(position);
//                if (te != null)
//                    tilesToRemove.put(position, te);
//            }
//            if(air == 65536) {
//                sections[sectionIndex] = null;
//            }
//        }
//        //
//    }
//
//    @Override
//    protected void loadFromChunk() {
//        net.minecraft.server.v1_15_R1.Chunk chunk = getNmsChunk();
//        ChunkSection[] sections = chunk.getSections();
//        for(int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
//            ChunkSection section = sections[sectionIndex];
//            for(int x = 0; x < 16; x++){
//                for(int y = 0; y < 16; y++) {
//                    for(int z = 0; z < 16; z++) {
//                        int block = section != null ? Block.getCombinedId(sections[sectionIndex].getType(x, y, z)) : 0;
//                        this.writeBlock(x, y + (sectionIndex << 4), z, block & 4095, (byte) (block >>> 12));
//                    }
//                }
//            }
//        }
//    }
//
//}