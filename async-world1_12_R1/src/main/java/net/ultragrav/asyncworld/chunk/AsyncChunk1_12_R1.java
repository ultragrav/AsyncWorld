package net.ultragrav.asyncworld.chunk;

import net.ultragrav.asyncworld.AsyncChunk;
import net.ultragrav.asyncworld.AsyncWorld;
import net.ultragrav.asyncworld.ChunkLocation;
import net.minecraft.server.v1_12_R1.*;
import net.ultragrav.asyncworld.nbt.*;
import net.ultragrav.utils.Vector3D;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
        getTiles().forEach((intVector3D, te) -> {
            BlockPosition bp = new BlockPosition(intVector3D.getX(), intVector3D.getY(), intVector3D.getZ());
            TileEntity entity = nmsChunk.getWorld().getTileEntity(bp); //Get or Create tile entity or null if none is applicable to the block at that position
            if(entity != null) {
                //Set Tile Entity's Coordinates in it's NBT
                te.getData().put("x", new TagInt(bp.getX()));
                te.getData().put("y", new TagInt(bp.getY()));
                te.getData().put("z", new TagInt(bp.getZ()));

                entity.load(fromGenericCompound(te)); //Load NBT into tile entity
            }
        });

        getTiles().clear();

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
            if ((sectionMask >> sectionIndex & 1) == 0)
                continue;

            ChunkSection section = sections[sectionIndex];
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        int block = section != null ? Block.getCombinedId(sections[sectionIndex].getType(x, y, z)) : 0;
                        this.writeBlock(x, y + (sectionIndex << 4), z, block & 4095, (byte) (block >>> 12));
                        BlockPosition position = new BlockPosition(x + (this.getLoc().getX() << 4), y + (sectionIndex << 4), z + (this.getLoc().getZ() << 4));
                        TileEntity entity = chunk.getTileEntities().get(position);
                        if(entity != null) {
                            this.setTileEntity(position.getX(), position.getY(), position.getZ(), fromNMSCompound(entity.save(new NBTTagCompound())));
                            Bukkit.broadcastMessage("Found tile entity while loading chunk. Tile Entity at " + position.getX() + " " + position.getY() + " " + position.getZ());
                        } else {
                            this.setTileEntity(position.getX(), position.getY(), position.getZ(), null); //Removes it from tiles if argument is null
                        }
                    }
                }
            }
        }
    }

    private TagCompound fromNMSCompound(NBTTagCompound compound) {
        return (TagCompound) fromNMSTag(compound);
    }

    private static Field fieldLongArray;

    static {
        try {
            fieldLongArray = NBTTagLongArray.class.getDeclaredField("b");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private Tag fromNMSTag(NBTBase base) {
        if(base instanceof NBTTagCompound) {
            TagCompound compound = new TagCompound();
            for(String key : ((NBTTagCompound)base).c()) {
                compound.getData().put(key, fromNMSTag(((NBTTagCompound)base).get(key)));
            }
            return compound;
        } else if(base instanceof NBTTagList) {
            TagList list = new TagList();
            for(int i = 0; i < ((NBTTagList)base).size(); i++) {
                list.getData().add(fromNMSTag(((NBTTagList)base).get(i)));
            }
            return list;
        } else if(base instanceof NBTTagLongArray) {
            try {
                return new TagLongArray((long[]) fieldLongArray.get(base));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                return new TagLongArray(new long[0]);
            }
        } else if(base instanceof NBTTagShort) {
            return new TagShort(((NBTTagShort)base).f());
        } else if(base instanceof NBTTagLong) {
            return new TagLong(((NBTTagLong)base).d());
        } else if(base instanceof NBTTagInt) {
            return new TagInt(((NBTTagInt)base).e());
        } else if(base instanceof NBTTagByte) {
            return new TagByte(((NBTTagByte)base).g());
        } else if(base instanceof NBTTagIntArray) {
            return new TagIntArray(((NBTTagIntArray)base).d());
        } else if(base instanceof NBTTagDouble) {
            return new TagDouble(((NBTTagDouble)base).asDouble());
        } else if(base instanceof NBTTagByteArray) {
            return new TagByteArray(((NBTTagByteArray)base).c());
        } else if(base instanceof NBTTagEnd) {
            return new TagEnd();
        } else if(base instanceof NBTTagFloat) {
            return new TagFloat(((NBTTagFloat)base).i());
        } else if(base instanceof NBTTagString) {
            return new TagString(((NBTTagString)base).c_());
        }
        throw new IllegalArgumentException("NBTTag is not of a recognized type (" + base.getClass().getName() + ")");
    }


    private NBTTagCompound fromGenericCompound(TagCompound compound) {
        return (NBTTagCompound) fromGenericTag(compound);
    }

    private NBTBase fromGenericTag(Tag tag) {
        if(tag instanceof TagCompound) {
            NBTTagCompound compound = new NBTTagCompound();
            Map<String, Tag> tags = ((TagCompound)tag).getData();
            tags.forEach((k, t) -> compound.set(k, fromGenericTag(t)));
            return compound;
        } else if(tag instanceof TagShort) {
            return new NBTTagShort(((TagShort)tag).getData());
        } else if(tag instanceof TagLong) {
            return new NBTTagLong(((TagLong)tag).getData());
        } else if(tag instanceof TagLongArray) {
            return new NBTTagLongArray(((TagLongArray)tag).getData());
        } else if(tag instanceof TagInt) {
            return new NBTTagInt(((TagInt)tag).getData());
        } else if(tag instanceof TagByte) {
            return new NBTTagByte(((TagByte)tag).getData());
        } else if(tag instanceof TagByteArray) {
            return new NBTTagByteArray(((TagByteArray)tag).getData());
        } else if(tag instanceof TagString) {
            return new NBTTagString(((TagString)tag).getData());
        } else if(tag instanceof TagList) {
            NBTTagList list = new NBTTagList();
            ((TagList)tag).getData().forEach(t -> list.add(fromGenericTag(t)));
            return list;
        } else if(tag instanceof TagIntArray) {
            return new NBTTagIntArray(((TagIntArray)tag).getData());
        } else if(tag instanceof TagFloat) {
            return new NBTTagFloat(((TagFloat)tag).getData());
        } else if(tag instanceof TagDouble) {
            return new NBTTagDouble(((TagDouble)tag).getData());
        }
        throw new IllegalArgumentException("Tag is not of a recognized type (" + tag.getClass().getName() + ")");
    }
}