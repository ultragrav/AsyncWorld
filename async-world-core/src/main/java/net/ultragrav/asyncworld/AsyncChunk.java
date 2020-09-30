package net.ultragrav.asyncworld;

import lombok.AccessLevel;
import lombok.Getter;
import net.ultragrav.asyncworld.nbt.TagCompound;
import net.ultragrav.utils.CuboidRegion;
import net.ultragrav.utils.IntVector3D;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public abstract class AsyncChunk implements Callable<AsyncChunk> {

    //Blocks location indexes stored as yzx

    //TODO calculate lighting (we can do this one tmw together)

    protected static short[] airFilled = new short[4096];

    public static byte[] CACHE_X = new byte[4096];
    public static byte[] CACHE_Y = new byte[4096];
    public static byte[] CACHE_Z = new byte[4096];

    public static short[][][] CACHE_INDEX = new short[16][16][16];


    static {
        Arrays.fill(airFilled, (short) -1);

        for(int i = 0; i < 4096; i++) {
            byte x = (byte) (i & 0xF);
            byte y = (byte) (i >>> 8);
            byte z = (byte) (i >>> 4 & 0xF);
            CACHE_X[i] = x;
            CACHE_Y[i] = y;
            CACHE_Z[i] = z;

            CACHE_INDEX[x][y][z] = (short) i;
        }
    }

    protected GUChunkSection[] chunkSections = new GUChunkSection[16];
    @Getter
    private ChunkLocation loc;
    @Getter
    protected volatile int editedSections;
    @Getter
    private AsyncWorld parent;

    @Getter(AccessLevel.PROTECTED)
    private final Map<IntVector3D, TagCompound> tiles = new ConcurrentHashMap<>();

    protected byte[] biomes = new byte[256];

    public AsyncChunk(AsyncWorld parent, ChunkLocation loc) {
        this.parent = parent;
        this.loc = loc;
        Arrays.fill(biomes, (byte) -1);
    }

    public int getLX(int loc) {
        return CACHE_X[loc];
    }

    public int getLY(int loc) {
        return CACHE_Y[loc];
    }

    public int getLZ(int loc) {
        return CACHE_Z[loc];
    }

    /**
     * Please only use with numbers <16
     */
    public int getCombinedLoc(int x, int y, int z) {
        return CACHE_INDEX[x][y][z];
    }

    public synchronized void setBiome(int x, int z, int biome) {
        biomes[z << 4 | x] = (byte) biome;
    }

    public synchronized int getBiome(int x, int z) {
        return biomes[z << 4 | x];
    }

    protected synchronized void writeBlock(int section, int index, int combinedBlockId, boolean addTile) {
        if (chunkSections[section] == null)
            chunkSections[section] = new GUChunkSection();

        chunkSections[section].contents[index] = (short) combinedBlockId;
        editedSections |= 1 << (section);
        int eIndex = index >>> 6;
        int eIndexIndex = index & 63;

        if (combinedBlockId != 0)
            chunkSections[section].edited[eIndex] |= 1L << eIndexIndex;
        else
            chunkSections[section].edited[eIndex] &= ~(1L << eIndexIndex);

        if (addTile && hasTileEntity(combinedBlockId & 0xFFF)) {
            setTileEntity(getLX(index), getLY(index) + (section << 4), getLZ(index), new TagCompound());
        }
    }

    protected synchronized void writeBlock(int section, int index, int combinedBlockId) {
        writeBlock(section, index, combinedBlockId, true);
    }

    public synchronized void writeBlock(int x, int y, int z, int id, byte data) {
        writeBlock(x, y, z, id, data, true);
    }

    public synchronized void writeBlock(int x, int y, int z, int id, byte data, boolean addTile) {
        if (id < 0 && id != -1)
            throw new IllegalArgumentException("ID cannot be less than 0 (air)");
        if(x > 15 || z > 15)
            throw new IllegalArgumentException("Cannot set block at chunk coordinates: " + x + " " + y + " " + z + " (out of bounds)");
        if (y < 0)
            return;
        if (y > 255)
            return;
        if (id == 0) {
            id = -1;
        } else if (id == -1) {
            id = 0;
            data = 0;
        }
        writeBlock(y >>> 4, getCombinedLoc(x & 0xF, y & 0xF, z & 0xF), ((data & 0xF) << 12 | (id > 0 ? id & 0xFFF : id) & 0xFFFF), addTile);
    }

    public synchronized void setEmittedLight(int x, int y, int z, int value) {
        if(y > 255)
            return;
        value = value & 0xF;
        int section = y >>> 4;
        int index = getCombinedLoc(x, y & 0xF, z);
        int part = index & 1;
        index >>>= 1;

        GUChunkSection section1 = chunkSections[section];
        if (section1 == null)
            section1 = chunkSections[section] = new GUChunkSection();

        int val = value << (part * 4);
        int filter = 0xF << ((part ^ 1) * 4);

        section1.emittedLight[index] = (byte) (section1.emittedLight[index] & filter | val);
    }

    public synchronized int getEmittedLight(int x, int y, int z) {
        int section = y >>> 4;
        int index = getCombinedLoc(x, y & 0xF, z);
        int part = index & 1;
        index >>>= 1;

        if (chunkSections[section] == null)
            return 0;

        return chunkSections[section].emittedLight[index] >>> (part * 4) & 0xF;
    }

    public synchronized void setTileEntity(int x, int y, int z, TagCompound tag) {
        IntVector3D vec = new IntVector3D((this.getLoc().getX() << 4) + x, y, (this.getLoc().getZ() << 4) + z);
        if (tag == null) {
            this.tiles.remove(vec);
        } else {
            this.tiles.put(vec, tag);
            editedSections |= 1 << (y >> 4);
        }
    }

    public synchronized TagCompound getTile(int x, int y, int z) {
        IntVector3D vec = new IntVector3D((this.getLoc().getX() << 4) + x, y, (this.getLoc().getZ() << 4) + z);
        return this.tiles.get(vec);
    }

    private synchronized void _merge(AsyncChunk parent) {
        parent.tiles.putAll(this.tiles);
        parent.biomes = biomes;
        GUChunkSection[] sections = this.chunkSections;
        for (int i = 0, sectionsLength = sections.length; i < sectionsLength; i++) {
            GUChunkSection section = sections[i];
            if (section == null)
                continue;
            if ((editedSections >>> i & 1) == 1) {
                if (parent.chunkSections[i] == null) {
                    parent.chunkSections[i] = section;
                } else {
                    GUChunkSection parentSection = parent.chunkSections[i];
                    short[] contents = section.contents;
                    for (int j = 0, contentsLength = contents.length; j < contentsLength; j++) {
                        short s = contents[j];
                        if (s != 0) {
                            parentSection.contents[j] = s;
                        }
                    }
                    System.arraycopy(section.emittedLight, 0, parentSection.emittedLight, 0, section.emittedLight.length);
                }
                parent.chunkSections[i].edited = new long[64];
            }
        }
        parent.editedSections |= this.editedSections;
    }

    /**
     * Basically adds the chunks edits to this
     */
    public synchronized void merge(AsyncChunk chunk) {
        chunk._merge(this);
    }

    /**
     * Loads the actual chunk's data into this instance
     * NOTE: This is not done on creation so if you need to read blocks, refresh manually
     */
    public synchronized void refresh(int sectionMask) {
        this.loadFromChunk(sectionMask);
    }

    public synchronized int readBlock(int x, int y, int z) {
        if (y < 0)
            return -1;
        if (y > 255)
            return -1;
        GUChunkSection section = chunkSections[y >> 4];
        if (section == null)
            return -1;
        short data = section.contents[getCombinedLoc(x, y & 0xF, z)];
        return data == 0 ? -1 : (data == -1 ? 0 : (data & 0xFFFF));
    }

    public abstract void sendPackets(int mask);

    public abstract int getCombinedBlockSync(int x, int y, int z);

    public synchronized void optimize() {
        for (int i = 0; i < chunkSections.length; i++) {
            OUTER: if (chunkSections[i] != null) {
                for (long l : chunkSections[i].edited) {
                    if (l != -1L) {
                        break OUTER;
                    }
                }
                optimizeSection(i, chunkSections[i]);
            }
        }
    }

    protected abstract void optimizeSection(int index, GUChunkSection section);

    /**
     * Must be called sync
     */
    public synchronized AsyncChunk call() {
        if (!isEdited())
            return null;
        this.update();
        this.editedSections = 0;
        this.chunkSections = new GUChunkSection[16];
        return this;
    }

    public synchronized void setIgnore(int x, int y, int z) {
        writeBlock(x, y, z, -1, (byte) 0);
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

    public abstract int syncGetEmittedLight(int x, int y, int z);
    public abstract int syncGetSkyLight(int x, int y, int z);
    public abstract int syncGetBrightnessOpacity(int x, int y, int z);
    public abstract void syncSetEmittedLight(int x, int y, int z, int value);
    public abstract void syncSetSkyLight(int x, int y, int z, int value);
    public abstract Map<IntVector3D, TagCompound> syncGetTiles();
    public abstract List<TagCompound> syncGetEntities();
    public abstract int[] syncGetHeightMap();
    public abstract void syncGetBlocksAndData(byte[] blocks, byte[] data, int section);
    public abstract byte[] syncGetEmittedLight(int section);
    public abstract byte[] syncGetSkyLight(int section);
    public abstract short getSectionBitMask();
    public abstract byte[] syncGetBiomes();
    public abstract boolean sectionExists(int section);

    public abstract void loadTiles();

    protected abstract void update();

    protected abstract void loadFromChunk(int sectionMask);

    public static class GUChunkSection {
        public short[] contents = new short[4096];
        public byte[] emittedLight = new byte[2048];
        public long[] edited = new long[64];
    }

    public static boolean[] CACHE_TILE = new boolean[4096];

    static {
        for (int i = 0, length = CACHE_TILE.length; i < length; i++) {
            CACHE_TILE[i] = _hasTileEntity(i);
        }
    }

    public static boolean hasTileEntity(int id) {
        if (id > CACHE_TILE.length)
            return true;
        return CACHE_TILE[id];
    }

    public static boolean _hasTileEntity(int id) {
        //Credit to FastAsyncWorldEdit by boydti
        switch (id) {
            case 26:
            case 218:
            case 54:
            case 130:
            case 142:
            case 27:
            case 137:
            case 188:
            case 189:
            case 52:
            case 154:
            case 84:
            case 25:
            case 144:
            case 138:
            case 176:
            case 177:
            case 63:
            case 119:
            case 68:
            case 323:
            case 117:
            case 116:
            case 28:
            case 66:
            case 157:
            case 61:
            case 62:
            case 140:
            case 146:
            case 149:
            case 150:
            case 158:
            case 23:
            case 123:
            case 124:
            case 29:
            case 33:
            case 151:
            case 178:
            case 209:
            case 210:
            case 211:
            case 255:
            case 219:
            case 220:
            case 221:
            case 222:
            case 223:
            case 224:
            case 225:
            case 226:
            case 227:
            case 228:
            case 229:
            case 230:
            case 231:
            case 232:
            case 233:
            case 234:
                return true;
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 24:
            case 30:
            case 31:
            case 32:
            case 34:
            case 35:
            case 36:
            case 37:
            case 38:
            case 39:
            case 40:
            case 41:
            case 42:
            case 43:
            case 44:
            case 45:
            case 46:
            case 47:
            case 48:
            case 49:
            case 50:
            case 51:
            case 53:
            case 55:
            case 56:
            case 57:
            case 58:
            case 59:
            case 60:
            case 64:
            case 65:
            case 67:
            case 69:
            case 70:
            case 71:
            case 72:
            case 73:
            case 74:
            case 75:
            case 76:
            case 77:
            case 78:
            case 79:
            case 80:
            case 81:
            case 82:
            case 83:
            case 85:
            case 86:
            case 87:
            case 88:
            case 89:
            case 90:
            case 91:
            case 92:
            case 93:
            case 94:
            case 95:
            case 96:
            case 97:
            case 98:
            case 99:
            case 100:
            case 101:
            case 102:
            case 103:
            case 104:
            case 105:
            case 106:
            case 107:
            case 108:
            case 109:
            case 110:
            case 111:
            case 112:
            case 113:
            case 114:
            case 115:
            case 118:
            case 120:
            case 121:
            case 122:
            case 125:
            case 126:
            case 127:
            case 128:
            case 129:
            case 131:
            case 132:
            case 133:
            case 134:
            case 135:
            case 136:
            case 139:
            case 141:
            case 143:
            case 145:
            case 147:
            case 148:
            case 152:
            case 153:
            case 155:
            case 156:
            case 159:
            case 160:
            case 161:
            case 162:
            case 163:
            case 164:
            case 165:
            case 166:
            case 167:
            case 168:
            case 169:
            case 170:
            case 171:
            case 172:
            case 173:
            case 174:
            case 175:
            case 179:
            case 180:
            case 181:
            case 182:
            case 183:
            case 184:
            case 185:
            case 186:
            case 187:
            case 190:
            case 191:
            case 192:
            case 193:
            case 194:
            case 195:
            case 196:
            case 197:
            case 198:
            case 199:
            case 200:
            case 201:
            case 202:
            case 203:
            case 204:
            case 205:
            case 206:
            case 207:
            case 208:
            case 212:
            case 213:
            case 214:
            case 215:
            case 216:
            case 217:
            case 235:
            case 236:
            case 237:
            case 238:
            case 239:
            case 240:
            case 241:
            case 242:
            case 243:
            case 244:
            case 245:
            case 246:
            case 247:
            case 248:
            case 249:
            case 250:
            case 251:
            case 252:
                return false;
            default:
                return id > 252;
        }
    }
}
