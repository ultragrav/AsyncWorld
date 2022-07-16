package net.ultragrav.asyncworld.chunk;

import net.minecraft.server.v1_8_R3.*;
import net.ultragrav.asyncworld.AsyncChunk;
import net.ultragrav.asyncworld.AsyncWorld;
import net.ultragrav.asyncworld.ChunkLocation;
import net.ultragrav.nbt.conversion.NBTConversion;
import net.ultragrav.nbt.wrapper.Tag;
import net.ultragrav.nbt.wrapper.TagCompound;
import net.ultragrav.nbt.wrapper.TagInt;
import net.ultragrav.utils.IntVector3D;
import org.bukkit.craftbukkit.v1_8_R3.CraftChunk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

@SuppressWarnings("unchecked")
public class AsyncChunk1_8_R3 extends AsyncChunk {
    public AsyncChunk1_8_R3(AsyncWorld parent, ChunkLocation loc) {
        super(parent, loc);
    }

    private final Map<BlockPosition, TileEntity> tilesToRemove = new HashMap<>();

    private final int[] airCount = new int[16];
    private static final byte[] BIOME_DEFAULT = new byte[256];

    private static Field fieldPalette;
    private static Field fieldTickingBlockCount;
    private static Field fieldNonEmptyBlockCount;

    private static Method methodGetPlayerChunk;
    private static Field fieldPlayers;

    private Chunk nmsCachedChunk = null;

    static {
        try {
            fieldNonEmptyBlockCount = ChunkSection.class.getDeclaredField("nonEmptyBlockCount");
            fieldTickingBlockCount = ChunkSection.class.getDeclaredField("tickingBlockCount");
            fieldPalette = ChunkSection.class.getDeclaredField("blockIds");
            fieldPalette.setAccessible(true);
            fieldTickingBlockCount.setAccessible(true);
            fieldNonEmptyBlockCount.setAccessible(true);

            methodGetPlayerChunk = PlayerChunkMap.class.getDeclaredMethod("a", int.class, int.class, boolean.class);
            methodGetPlayerChunk.setAccessible(true);
            fieldPlayers = methodGetPlayerChunk.getReturnType().getDeclaredField("b");
            fieldPlayers.setAccessible(true);
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        Arrays.fill(BIOME_DEFAULT, (byte) -1);
    }

    private void validateCachedChunk() {
        if (nmsCachedChunk == null) {
            if (!getParent().getBukkitWorld().isChunkLoaded(this.getLoc().getX(), this.getLoc().getZ())) {
                return;
            }
            nmsCachedChunk = ((CraftChunk) getParent().getBukkitWorld().getChunkAt(this.getLoc().getX(), this.getLoc().getZ())).getHandle();
        }
    }

    @Override
    public int getCombinedBlockSync(int x, int y, int z) {
        Chunk nmsChunk = getNmsChunk();
        ChunkSection[] sections = nmsChunk.getSections();
        ChunkSection section = sections[y >>> 4];
        if (section == null) {
            return 0;
        }
        IBlockData data = section.getType(x, y & 15, z);
        return Block.getCombinedId(data);
    }

    @Override
    public void setCombinedBlockSync(int x, int y, int z, int combinedBlock) {
        Chunk nmsChunk = getNmsChunk();
        ChunkSection[] sections = nmsChunk.getSections();
        ChunkSection section = sections[y >>> 4];
        if (section == null) {
            sections[y >>> 4] = section = new ChunkSection(y & (~0xF), getBukkitChunk().getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL);
        }
        IBlockData data = Block.getByCombinedId(combinedBlock);
        section.setType(x, y & 15, z, data);
    }

    @Override
    protected void optimizeSection(int index, GUChunkSection section) {
//        DataPaletteBlock palette = new DataPaletteBlock();
//        int airCount = 0;
//        for (int i = 0, length = section.contents.length; i < length; i++) {
//            short block = section.contents[i];
//            if (block == 0 || block == -1) {
//                airCount++;
//                continue;
//            }
//            palette.setBlock(getLX(i), getLY(i), getLZ(i), Block.getByCombinedId(block));
//        }
//        this.airCount[index] = airCount;
//        optimizedSections[index] = palette;
        // TODO: No DataPaletteBlock
    }

    public static void setCount(int tickingBlockCount, int nonEmptyBlockCount, ChunkSection section) throws NoSuchFieldException, IllegalAccessException {
        fieldTickingBlockCount.set(section, tickingBlockCount);
        fieldNonEmptyBlockCount.set(section, nonEmptyBlockCount);
    }

    // TODO wtf is DataPaletteBlock anyway
//    public void setPalette(ChunkSection section, DataPaletteBlock palette) throws NoSuchFieldException, IllegalAccessException {
//        fieldPalette.set(section, palette); // This should be a char[]
//    }

    private Chunk getNmsChunk() {
        validateCachedChunk();
        return nmsCachedChunk;
    }

    @Override
    public void start() {
        getBukkitChunk().load(true);
    }

    @Override
    public void end(int mask) {
        Chunk nmsChunk = getNmsChunk();
        long ms = System.currentTimeMillis();

        //Remove tile entity
        tilesToRemove.forEach((bp, te) -> {
            nmsChunk.world.t(bp); //Remove it from the world
            nmsChunk.getTileEntities().remove(bp); //Remove it from the chunk
            te.y(); // Set removed to true
            te.E(); // Set tile entity's parent block to null
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

                entity.a(fromGenericCompound(te)); // Load NBT into tile entity
            }
        });

        getTiles().clear();
        ms = System.currentTimeMillis() - ms;
        ms = System.currentTimeMillis();
        this.sendPackets(mask);
        ms = System.currentTimeMillis() - ms;
    }

    @Override
    public int syncGetEmittedLight(int x, int y, int z) {
        validateCachedChunk();
        if (nmsCachedChunk == null)
            return 0;
        int sectionIndex = y >> 4;
        if (nmsCachedChunk.getSections()[sectionIndex] == null) {
            return 0;
        }
        return nmsCachedChunk.getSections()[sectionIndex].getEmittedLightArray().a(x, y & 15, z);
    }

    @Override
    public int syncGetSkyLight(int x, int y, int z) {
        validateCachedChunk();
        if (nmsCachedChunk == null)
            return 0;
        int sectionIndex = y >> 4;
        if (nmsCachedChunk.getSections()[sectionIndex] == null) {
            return 0;
        }
        return nmsCachedChunk.getSections()[sectionIndex].getSkyLightArray().a(x, y & 15, z);
    }

    @Override
    public int syncGetBrightnessOpacity(int x, int y, int z) {
        validateCachedChunk();
        if (nmsCachedChunk == null)
            return 0;
        int sectionIndex = y >> 4;
        if (nmsCachedChunk.getSections()[sectionIndex] == null) {
            return 0;
        }
        IBlockData data = nmsCachedChunk.getSections()[sectionIndex].getType(x, y & 15, z);
        return data.getBlock().getMaterial().r().L; // TODO: Check
    }

    @Override
    public void syncSetEmittedLight(int x, int y, int z, int value) {
        validateCachedChunk();
        if (nmsCachedChunk == null)
            return;
        int sectionIndex = y >> 4;
        if (nmsCachedChunk.getSections()[sectionIndex] == null) {
            nmsCachedChunk.getSections()[sectionIndex] = new ChunkSection(sectionIndex << 4, nmsCachedChunk.getWorld().getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL);
        }
        nmsCachedChunk.getSections()[sectionIndex].getEmittedLightArray().a(x, y & 15, z, value);
    }

    @Override
    public void syncSetSkyLight(int x, int y, int z, int value) {
        validateCachedChunk();
        if (nmsCachedChunk == null)
            return;
        int sectionIndex = y >> 4;
        if (nmsCachedChunk.getSections()[sectionIndex] == null) {
            nmsCachedChunk.getSections()[sectionIndex] = new ChunkSection(sectionIndex << 4, nmsCachedChunk.getWorld().getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL);
        }
        nmsCachedChunk.getSections()[sectionIndex].getSkyLightArray().a(x, y & 15, z, value);
    }

    @Override
    public Map<IntVector3D, TagCompound> syncGetTiles() {
        validateCachedChunk();
        Map<IntVector3D, TagCompound> list = new HashMap<>();

        nmsCachedChunk.getTileEntities().forEach((p, t) -> {
            if (t == null)
                return;
            NBTTagCompound compound = new NBTTagCompound();
            t.b(compound);
            list.put(new IntVector3D(p.getX(), p.getY(), p.getZ()), fromNMSCompound(compound));
        });
        return list;
    }

    @Override //Not async safe
    public List<TagCompound> syncGetEntities() {
        List<TagCompound> out = new ArrayList<>();
        for (int i = 0; i < getNmsChunk().getEntitySlices().length; i++) {
            if (nmsCachedChunk.getEntitySlices()[i] == null)
                continue;
            for (Entity entity : nmsCachedChunk.getEntitySlices()[i]) {
                //All entities in the i-th section
                NBTTagCompound nmsCompound = new NBTTagCompound();
                if (entity.d(nmsCompound)) {
                    TagCompound compound = fromNMSCompound(nmsCompound);
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
        if (data.length < 2048 || blocks.length < 4096)
            return;
        ChunkSection sect = getNmsChunk().getSections()[section];
        if (sect == null)
            return;

        //sect.getBlocks().exportData(blocks, new NibbleArray(data));
        sect.getIdArray(); // TODO: Check if this contains block data values
    }

    @Override
    public byte[] syncGetEmittedLight(int section) {
        Chunk chunk = getNmsChunk();
        ChunkSection sect = chunk.getSections()[section];
        if (sect == null)
            return null;
        byte[] arr = new byte[2048];
        System.arraycopy(chunk.getSections()[section].getEmittedLightArray().a(), 0, arr, 0, arr.length); // TODO: Verify
        return arr;
    }

    @Override
    public byte[] syncGetSkyLight(int section) {
        Chunk chunk = getNmsChunk();
        ChunkSection sect = chunk.getSections()[section];
        if (sect == null)
            return null;
        byte[] arr = new byte[2048];
        System.arraycopy(chunk.getSections()[section].getSkyLightArray().a(), 0, arr, 0, arr.length); // TODO: Verify
        return arr;
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
    public boolean sectionExists(int section) {
        if (getNmsChunk() == null)
            return false;
        return getNmsChunk().getSections()[section] != null;
    }

    @Override
    public byte[] syncGetBiomes() {
        Chunk chunk = getNmsChunk();
        byte[] arr = new byte[chunk.getBiomeIndex().length];
        System.arraycopy(chunk.getBiomeIndex(), 0, arr, 0, arr.length);
        return arr;
    }

    public Object getSection(int sectionIndex) {
        validateCachedChunk();
        return nmsCachedChunk.getSections()[sectionIndex];
    }

    @Override
    public synchronized void loadTiles() {

        validateCachedChunk();

        getTiles().clear();

        nmsCachedChunk.getTileEntities().forEach((p, t) -> {
            if (t == null)
                return;
            NBTTagCompound compound = new NBTTagCompound();
            t.b(compound);
            this.setTileEntity(p.getX() & 0xF, p.getY(), p.getZ() & 0xF, fromNMSCompound(compound));
        });
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
            packet = new PacketPlayOutMapChunk(nmsChunk, false, 65280);
            secondMapPacket = new PacketPlayOutMapChunk(nmsChunk, false, 255);
        } else {
            packet = new PacketPlayOutMapChunk(nmsChunk, false, mask);
            secondMapPacket = null;
        }

        List<Packet<?>> tilePackets = new ArrayList<>();
        nmsChunk.getTileEntities().forEach((key, value) -> tilePackets.add(value.getUpdatePacket()));

        try {
            PlayerChunkMap map = ((WorldServer) nmsChunk.getWorld()).getPlayerChunkMap();
            Object playerChunk = methodGetPlayerChunk.invoke(map, loc.getX(), loc.getZ(), false);
            if (playerChunk == null) {
                return;
            }
            List<EntityPlayer> players = (List<EntityPlayer>) fieldPlayers.get(playerChunk);
            players.forEach(p -> {
                p.playerConnection.sendPacket(packet);
                if (secondMapPacket != null) {
                    p.playerConnection.sendPacket(secondMapPacket);
                }
                tilePackets.forEach(packet1 -> p.playerConnection.sendPacket(packet1));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void update() {
        //long ms = System.nanoTime();
        ChunkLocation loc = this.getLoc();
        Chunk nmsChunk = getNmsChunk();
        nmsCachedChunk = null;

        nmsChunk.mustSave = true;
        nmsChunk.f(true);

        int bx = loc.getX() << 4;
        int bz = loc.getZ() << 4;

        boolean noTiles = nmsChunk.getTileEntities().isEmpty();

        int remMask = 0;

        int compEdited = 0;

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
                long[] edited = guChunkSection.edited;
                for (int i = 0, editedLength = edited.length; i < editedLength; i++) {
                    long l = edited[i];
                    if (l != -1L) {
                        completelyEdited = false;
                        break;
                    }
                }
            }

            if (completelyEdited) {
                compEdited++;
                if (section == null)
                    section = sections[sectionIndex] = new ChunkSection(sectionIndex << 4, nmsChunk.getWorld().getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL);
                System.arraycopy(guChunkSection.emittedLight, 0,
                        section.getEmittedLightArray().a(), 0, guChunkSection.emittedLight.length);

                if (this.isFullSkyLight())
                    Arrays.fill(section.getSkyLightArray().a(), (byte) 0xFF);

                // TODO: how optimization without DataPaletteBlock?
//                if (optimizedSections[sectionIndex] != null) {
//                    try {
//                        setPalette(section, optimizedSections[sectionIndex]); //Set palette
//                        setCount(0, 4096 - airCount[sectionIndex], section); //Set non-air-block count
//                    } catch (NoSuchFieldException | IllegalAccessException e) {
//                        e.printStackTrace();
//                    }
//                    remMask |= 1 << sectionIndex;
//                    continue;
//                }
            }

            short[] sectionContents = guChunkSection.contents;

            int air = 0;

            for (int i = 0; i < 4096; i++) {

                short block = sectionContents[i];

                int lx = CACHE_X[i];
                int ly = CACHE_Y[i];
                int lz = CACHE_Z[i];

                if (block == -2 || block == 0)
                    continue;

                if (block == -1) {
                    block = 0;
                    air++;
                }

                IBlockData bd = Block.getByCombinedId(block & 0xFFFF);
                section.setType(lx, ly, lz, bd);
                if (!completelyEdited) {
                    int index = i;
                    int part = index & 1;
                    index >>>= 1;
                    int emittedLight = (guChunkSection.emittedLight[index] >>> (part << 2) & 0xF);
                    section.getEmittedLightArray().a(lx, ly, lz, emittedLight);

                    if (this.fullSkyLight)
                        section.getSkyLightArray().a(lx, ly, lz, 0xF);
                }

                //Remove tile entity
                if (!noTiles) {
                    BlockPosition position = new BlockPosition(lx + bx, ly + (sectionIndex << 4), lz + bz);
                    TileEntity te = nmsChunk.tileEntities.get(position);
                    if (te != null) {
                        tilesToRemove.put(position, te);
                    }
                }
            }


        }


        //Biomes
        byte[] chunkBiomes = nmsChunk.getBiomeIndex();
        for (int i = 0; i < chunkBiomes.length && i < biomes.length; i++)
            if (biomes[i] != -1)
                chunkBiomes[i] = biomes[i];
        System.arraycopy(BIOME_DEFAULT, 0, biomes, 0, biomes.length);

        //Rem more tiles (from completely edited sections)
        if (remMask != 0) {
            int finalRemMask = remMask;
            nmsChunk.getTileEntities().forEach((p, t) -> {
                if ((finalRemMask & (1 << (p.getY() >> 4))) != 0) {
                    if (t != null)
                        tilesToRemove.put(p, t);
                }
            });
        }

        //heightmap/lighting
        nmsChunk.initLighting();

        //Cleanup TODO
        //optimizedSections = new DataPaletteBlock[16];

        //ms = System.nanoTime() - ms;
        //System.out.println("Chunk update took: " + ms + "ns comp edits: " + compEdited);
    }

    @Override
    protected void loadFromChunk(int sectionMask) {
        Chunk chunk = getNmsChunk();
        ChunkSection[] sections = chunk.getSections();

        //Clear tiles in the specified sections
        Map<IntVector3D, TagCompound> tiles = new HashMap<>(getTiles());
        tiles.forEach((p, t) -> {
            if (((sectionMask >>> (p.getY() >> 4)) & 1) == 0)
                setTileEntity(p.getX() & 0xF, p.getY(), p.getZ() & 0xF, null);
        });

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            if ((sectionMask >>> sectionIndex & 1) == 0)
                continue;

            ChunkSection section = sections[sectionIndex];
            if (section == null) { //Section is null (filled with air)
                GUChunkSection section1 = this.chunkSections[sectionIndex];
                if (section1 == null)
                    section1 = this.chunkSections[sectionIndex] = new GUChunkSection();
                System.arraycopy(AsyncChunk.airFilled, 0, section1.contents, 0, AsyncChunk.airFilled.length);
                continue;
            }

            for (int i = 0; i < 4096; i++) {
                int x = getLX(i);
                int y = getLY(i);
                int z = getLZ(i);

                int block = Block.getCombinedId(section.getType(x, y, z));

                this.writeBlock(sectionIndex, i, block & 0xFFFF, false);
            }

            //Emitted light
            System.arraycopy(section.getEmittedLightArray().a(), 0, chunkSections[sectionIndex].emittedLight, 0, section.getEmittedLightArray().a().length);
        }

        //Do this after writing blocks because writing blocks may set tile entities
        //Load tile entities in specified sections
        chunk.getTileEntities().forEach((p, t) -> {
            if (t == null)
                return;
            if (((sectionMask >>> (p.getY() >> 4)) & 1) == 0)
                return;
            NBTTagCompound compound = new NBTTagCompound();
            t.b(compound);
            this.setTileEntity(p.getX() & 0xF, p.getY(), p.getZ() & 0xF, fromNMSCompound(compound));
        });

    }

    public static TagCompound fromNMSCompound(NBTTagCompound compound) {
        return (TagCompound) fromNMSTag(compound);
    }

    public static Tag fromNMSTag(NBTBase base) {
        return NBTConversion.wrapTag(base);
    }


    public static NBTTagCompound fromGenericCompound(TagCompound compound) {
        return (NBTTagCompound) fromGenericTag(compound);
    }

    public static NBTBase fromGenericTag(Tag tag) {
        return NBTConversion.unwrapTag(tag, "v1_8_R3");
    }
}