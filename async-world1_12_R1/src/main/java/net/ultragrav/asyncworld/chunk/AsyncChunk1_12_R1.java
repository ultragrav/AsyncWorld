package net.ultragrav.asyncworld.chunk;

import net.ultragrav.asyncworld.AsyncChunk;
import net.ultragrav.asyncworld.AsyncWorld;
import net.ultragrav.asyncworld.ChunkLocation;
import net.minecraft.server.v1_12_R1.*;
import net.ultragrav.asyncworld.nbt.*;
import net.ultragrav.utils.IntVector3D;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;

import java.lang.reflect.Field;
import java.util.*;

public class AsyncChunk1_12_R1 extends AsyncChunk {
    public AsyncChunk1_12_R1(AsyncWorld parent, ChunkLocation loc) {
        super(parent, loc);
    }

    private final Map<BlockPosition, TileEntity> tilesToRemove = new HashMap<>();

    private DataPaletteBlock[] optimizedSections = new DataPaletteBlock[16];
    private final int[] airCount = new int[16];

    private static Field fieldPalette;
    private static Field fieldTickingBlockCount;
    private static Field fieldNonEmptyBlockCount;

    static {
        try {
            fieldNonEmptyBlockCount = ChunkSection.class.getDeclaredField("nonEmptyBlockCount");
            fieldTickingBlockCount = ChunkSection.class.getDeclaredField("tickingBlockCount");
            fieldPalette = ChunkSection.class.getDeclaredField("blockIds");
            fieldPalette.setAccessible(true);
            fieldTickingBlockCount.setAccessible(true);
            fieldNonEmptyBlockCount.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    @Override
    public short getCombinedBlockSync(int x, int y, int z) {
        Chunk nmsChunk = getNmsChunk();
        ChunkSection[] sections = nmsChunk.getSections();
        ChunkSection section = sections[y >>> 4];
        if (section == null) {
            return 0;
        }
        IBlockData data = section.getType(x, y & 15, z);
        return (short) Block.getCombinedId(data);
    }

    @Override
    protected void optimizeSection(int index, GUChunkSection section) {
        DataPaletteBlock palette = new DataPaletteBlock();
        int airCount = 0;
        for (int i = 0, length = section.contents.length; i < length; i++) {
            short block = section.contents[i];
            if (block == 0 || block == -1) {
                airCount++;
                continue;
            }
            palette.setBlock(getLX(i), getLY(i), getLZ(i), Block.getByCombinedId(block));
        }
        this.airCount[index] = airCount;
        optimizedSections[index] = palette;
    }

    public void setCount(int tickingBlockCount, int nonEmptyBlockCount, ChunkSection section) throws NoSuchFieldException, IllegalAccessException {
        fieldTickingBlockCount.set(section, tickingBlockCount);
        fieldNonEmptyBlockCount.set(section, nonEmptyBlockCount);
    }

    public void setPalette(ChunkSection section, DataPaletteBlock palette) throws NoSuchFieldException, IllegalAccessException {
        fieldPalette.set(section, palette);
        Arrays.fill(section.getEmittedLightArray().asBytes(), (byte) 0);
    }

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
            if (entity != null) {
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

        boolean noTiles = nmsChunk.getTileEntities().isEmpty();

        ChunkSection[] sections = nmsChunk.getSections();
        for (int sectionIndex = 0; sectionIndex < 16; sectionIndex++) {
            if ((this.getEditedSections() >>> sectionIndex & 1) == 0)
                continue;
            ChunkSection section = sections[sectionIndex];

            GUChunkSection guChunkSection = chunkSections[sectionIndex];

            if (guChunkSection == null)
                continue;

            boolean completelyEdited = section == null;
            if (!completelyEdited) {
                completelyEdited = true;
                for (long l : guChunkSection.edited) {
                    if (l != -1L) {
                        completelyEdited = false;
                        break;
                    }
                }
            }

            if (completelyEdited) {
                if (section == null)
                    section = sections[sectionIndex] = new ChunkSection(sectionIndex << 4, true);
                System.arraycopy(guChunkSection.emittedLight, 0,
                        section.getEmittedLightArray().asBytes(), 0, guChunkSection.emittedLight.length);
                if (optimizedSections[sectionIndex] != null) {
                    try {
                        if(airCount[sectionIndex] == 4096) {
                            sections[sectionIndex] = null;
                        } else {
                            setPalette(section, optimizedSections[sectionIndex]); //Set palette
                            setCount(0, 4096 - airCount[sectionIndex], section); //Set non-air-block count
                            Arrays.fill(section.getSkyLightArray().asBytes(), (byte) 0xFF);
                        }
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
            }

            short[] sectionContents = guChunkSection.contents;

            int air = 0;

            for (int i = 0; i < 4096; i++) {

                short block = sectionContents[i];

                int lx = getLX(i);
                int ly = getLY(i);
                int lz = getLZ(i);

                if (block == -2 || block == 0)
                    continue;

                if (block == -1) {
                    block = 0;
                    air++;
                }

                section.setType(lx, ly, lz, Block.getByCombinedId(block & 0xFFFF));
                if(!completelyEdited) {
                    int index = i;
                    int part = index & 1;
                    index >>>= 1;
                    int emittedLight = (guChunkSection.emittedLight[index] >>> (part * 4) & 0xF);
                    section.getEmittedLightArray().a(lx, ly, lz, emittedLight);
                }

                //Remove tile entity
                if(!noTiles) {
                    BlockPosition position = new BlockPosition(lx + bx, ly + (sectionIndex << 4), lz + bz);
                    TileEntity te = nmsChunk.getTileEntities().get(position);
                    if (te != null)
                        tilesToRemove.put(position, te);
                }
            }
            if (air == 4096) {
                sections[sectionIndex] = null;
            }

            Arrays.fill(section.getSkyLightArray().asBytes(), (byte) 0xFF);
        }


        //Biomes
        byte[] chunkBiomes = nmsChunk.getBiomeIndex();
        for (int i = 0; i < chunkBiomes.length && i < biomes.length; i++)
            if (biomes[i] != -1)
                chunkBiomes[i] = biomes[i];
        Arrays.fill(biomes, (byte) -1);


        //heightmap/lighting
        nmsChunk.initLighting();

        //Cleanup
        optimizedSections = new DataPaletteBlock[16];
    }

    @Override
    protected void loadFromChunk(int sectionMask) {
        Chunk chunk = getNmsChunk();
        ChunkSection[] sections = chunk.getSections();

        Map<IntVector3D, TagCompound> tiles = new HashMap<>(getTiles());
        tiles.forEach((p, t) -> {
            if (((sectionMask >>> (p.getY() >> 4)) & 1) == 0)
                setTileEntity(p.getX() & 0xF, p.getY(), p.getZ() & 0xF, null);
        });

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            if ((sectionMask >>> sectionIndex & 1) == 0)
                continue;

            ChunkSection section = sections[sectionIndex];
            if (section == null) {
                GUChunkSection section1 = this.chunkSections[sectionIndex];
                if (section1 == null)
                    section1 = this.chunkSections[sectionIndex] = new GUChunkSection();
                System.arraycopy(AsyncChunk.airFilled, 0, section1.contents, 0, AsyncChunk.airFilled.length);
                continue;
            }

            for(int i = 0; i < 4096; ++i) {
                int x = i & 15;
                int y = i >> 8 & 15;
                int z = i >> 4 & 15;

                int block = Block.REGISTRY_ID.getId(section.getBlocks().a(x, y, z));

                short id = (short) (block >> 4 & 0xFF);
                if (id == 0) id = -1;
                byte dat = (byte) (block & 0xF);
                this.writeBlock(sectionIndex, i, (dat << 12 | id) & 0xFFFF, false);
            }

            //Emitted light
            System.arraycopy(section.getEmittedLightArray().asBytes(), 0, chunkSections[sectionIndex].emittedLight, 0, section.getEmittedLightArray().asBytes().length);
        }

        //Do this after writing blocks because writing blocks may set tile entities
        chunk.getTileEntities().forEach((p, t) -> {
            if (t == null)
                return;
            this.setTileEntity(p.getX() & 0xF, p.getY(), p.getZ() & 0xF, fromNMSCompound(t.save(new NBTTagCompound())));
        });

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

    public static Tag fromNMSTag(NBTBase base) {
        if (base instanceof NBTTagCompound) {
            TagCompound compound = new TagCompound();
            for (String key : ((NBTTagCompound) base).c()) {
                compound.getData().put(key, fromNMSTag(((NBTTagCompound) base).get(key)));
            }
            return compound;
        } else if (base instanceof NBTTagList) {
            TagList list = new TagList();
            for (int i = 0; i < ((NBTTagList) base).size(); i++) {
                list.getData().add(fromNMSTag(((NBTTagList) base).i(i)));
            }
            return list;
        } else if (base instanceof NBTTagLongArray) {
            try {
                return new TagLongArray((long[]) fieldLongArray.get(base));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                return new TagLongArray(new long[0]);
            }
        } else if (base instanceof NBTTagShort) {
            return new TagShort(((NBTTagShort) base).f());
        } else if (base instanceof NBTTagLong) {
            return new TagLong(((NBTTagLong) base).d());
        } else if (base instanceof NBTTagInt) {
            return new TagInt(((NBTTagInt) base).e());
        } else if (base instanceof NBTTagByte) {
            return new TagByte(((NBTTagByte) base).g());
        } else if (base instanceof NBTTagIntArray) {
            return new TagIntArray(((NBTTagIntArray) base).d());
        } else if (base instanceof NBTTagDouble) {
            return new TagDouble(((NBTTagDouble) base).asDouble());
        } else if (base instanceof NBTTagByteArray) {
            return new TagByteArray(((NBTTagByteArray) base).c());
        } else if (base instanceof NBTTagEnd) {
            return new TagEnd();
        } else if (base instanceof NBTTagFloat) {
            return new TagFloat(((NBTTagFloat) base).i());
        } else if (base instanceof NBTTagString) {
            return new TagString(((NBTTagString) base).c_());
        }
        throw new IllegalArgumentException("NBTTag is not of a recognized type (" + base.getClass().getName() + ")");
    }


    public static NBTTagCompound fromGenericCompound(TagCompound compound) {
        return (NBTTagCompound) fromGenericTag(compound);
    }

    public static NBTBase fromGenericTag(Tag tag) {
        if (tag instanceof TagCompound) {
            NBTTagCompound compound = new NBTTagCompound();
            Map<String, Tag> tags = ((TagCompound) tag).getData();
            tags.forEach((k, t) -> compound.set(k, fromGenericTag(t)));
            return compound;
        } else if (tag instanceof TagShort) {
            return new NBTTagShort(((TagShort) tag).getData());
        } else if (tag instanceof TagLong) {
            return new NBTTagLong(((TagLong) tag).getData());
        } else if (tag instanceof TagLongArray) {
            return new NBTTagLongArray(((TagLongArray) tag).getData());
        } else if (tag instanceof TagInt) {
            return new NBTTagInt(((TagInt) tag).getData());
        } else if (tag instanceof TagByte) {
            return new NBTTagByte(((TagByte) tag).getData());
        } else if (tag instanceof TagByteArray) {
            return new NBTTagByteArray(((TagByteArray) tag).getData());
        } else if (tag instanceof TagString) {
            return new NBTTagString(((TagString) tag).getData());
        } else if (tag instanceof TagList) {
            NBTTagList list = new NBTTagList();
            ((TagList) tag).getData().forEach(t -> list.add(fromGenericTag(t)));
            return list;
        } else if (tag instanceof TagIntArray) {
            return new NBTTagIntArray(((TagIntArray) tag).getData());
        } else if (tag instanceof TagFloat) {
            return new NBTTagFloat(((TagFloat) tag).getData());
        } else if (tag instanceof TagDouble) {
            return new NBTTagDouble(((TagDouble) tag).getData());
        }
        throw new IllegalArgumentException("Tag is not of a recognized type (" + tag.getClass().getName() + ")");
    }
}