package net.ultragrav.asyncworld.schematics;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ultragrav.asyncworld.AsyncWorld;
import net.ultragrav.asyncworld.nbt.TagCompound;
import net.ultragrav.serializer.GravSerializable;
import net.ultragrav.serializer.GravSerializer;
import net.ultragrav.serializer.compressors.ZstdCompressor;
import net.ultragrav.utils.*;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Schematic implements GravSerializable {

    private static final int FORMAT_VERSION = 4;

    private final IntVector3D origin;
    private final IntVector3D dimensions;
    private final int[][][] blocks;
    private final byte[][][] emittedLight;
    @Getter
    private Map<IntVector3D, TagCompound> tiles = new ConcurrentHashMap<>();
    private final int squareSize;
    private final int lineSize;

    public Schematic(GravSerializer serializer) {

        //If format doesn't exist its format 0
        int formatVersion = 0;
        try {
            formatVersion = serializer.readObject();
        } catch (Exception ignored) {
            serializer.reset();
        }
        if(formatVersion > 3) {
            this.dimensions = new IntVector3D(serializer.readInt(), serializer.readInt(), serializer.readInt());
            this.origin = new IntVector3D(serializer.readInt(), serializer.readInt(), serializer.readInt());
        } else {
            this.dimensions = serializer.readObject();
            this.origin = serializer.readObject();
        }
        blocks = ArrayUtils.castArrayToTripleInt(serializer.readObject());
        if (formatVersion > 0)
            tiles = new ConcurrentHashMap<>(serializer.readObject());
        if (formatVersion > 2) {
            emittedLight = castArrayToTripleByte(serializer.readObject());
        } else {
            emittedLight = new byte[dimensions.getY()][dimensions.getX()][dimensions.getZ()];
        }

        squareSize = dimensions.getY() * dimensions.getZ();
        lineSize = dimensions.getZ();
    }

    public Schematic(File file) throws IOException {
        this(new GravSerializer(new FileInputStream(file), ZstdCompressor.instance));
    }

    public Schematic(IntVector3D origin, IntVector3D dimensions, int[][][] blocks, byte[][][] emittedLight, Map<IntVector3D, TagCompound> tiles) {
        this.origin = origin;
        this.dimensions = dimensions;
        this.blocks = blocks;
        this.tiles = tiles;
        this.emittedLight = emittedLight;
        squareSize = dimensions.getY() * dimensions.getZ();
        lineSize = dimensions.getZ();
    }

    public Schematic(IntVector3D origin, IntVector3D dimensions, int[][][] blocks, Map<IntVector3D, TagCompound> tiles) {
        this.origin = origin;
        this.dimensions = dimensions;
        this.blocks = blocks;
        this.tiles = tiles;
        this.emittedLight = new byte[dimensions.getY()][dimensions.getX()][dimensions.getZ()];
        squareSize = dimensions.getY() * dimensions.getZ();
        lineSize = dimensions.getZ();
    }

    public Schematic(Schematic copy) {
        this.origin = copy.origin;
        this.dimensions = copy.dimensions;
        this.squareSize = copy.squareSize;
        this.lineSize = copy.lineSize;
        blocks = new int[copy.blocks.length][][];
        System.arraycopy(copy.blocks, 0, blocks, 0, blocks.length);
        tiles = new ConcurrentHashMap<>(copy.tiles); //Shallow copy, changes to nbt data in copied schematic will affect ones in this schematic -> may change later
        emittedLight = new byte[copy.blocks.length][][];
        System.arraycopy(copy.emittedLight, 0, emittedLight, 0, blocks.length);
    }

    public Schematic(IntVector3D origin, AsyncWorld world, CuboidRegion region) {
        this(origin, world, region, -1);
    }

    public Schematic(IntVector3D origin, AsyncWorld world, CuboidRegion region, int ignoreBlock) {
        this.origin = origin;
        this.dimensions = region.getMaximumPoint().subtract(region.getMinimumPoint()).add(Vector3D.ONE).asIntVector();
        this.blocks = new int[region.getHeight()][region.getWidth()][region.getLength()];
        this.emittedLight = new byte[region.getHeight()][region.getWidth()][region.getLength()];

        IntVector3D min = region.getMinimumPoint().asIntVector();

        squareSize = dimensions.getX() * dimensions.getZ();
        lineSize = dimensions.getZ();

        AtomicInteger count = new AtomicInteger();
        long time = System.currentTimeMillis();

        world.asyncForAllInRegion(region, (loc, block, tag, lighting) -> {
            IntVector3D relLoc = loc.asIntVector().subtract(min);

            count.getAndIncrement();

            if (block == ignoreBlock && ignoreBlock != -1)
                block = -1;
            blocks[relLoc.getY()][relLoc.getX()][relLoc.getZ()] = block;
            emittedLight[relLoc.getY()][relLoc.getX()][relLoc.getZ()] = (byte) (int) lighting;

            if (tag != null && block != -1) {
                tiles.put(relLoc, tag);
            }

            //Lighting
        }, true);

    }

    public int getBlockAt(IntVector3D relLoc) {
        return getBlockAt(relLoc.getY(), relLoc.getX(), relLoc.getZ());
    }

    public int getBlockAt(int x, int y, int z) {
        return this.blocks[y][x][z];
    }

    public IntVector3D getDimensions() {
        return this.dimensions;
    }

    public int getEmittedLight(int x, int y, int z) {
        return emittedLight[y][x][z] & 0xF;
    }


    public IntVector3D getOrigin() {
        return this.origin;
    }

    public Schematic copy() {
        return new Schematic(this);
    }

    public void save(File file) throws IOException {
        GravSerializer serializer = new GravSerializer();
        serialize(serializer);
        serializer.writeToStream(new FileOutputStream(file), ZstdCompressor.instance);
    }

    public Schematic rotate(int rotation) {
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("Invalid rotation");
        }
        if (rotation == 0) {
            return this.copy();
        }
        int mod = rotation % 2;
        IntVector3D newDimensions = new IntVector3D(dimensions);
        if (mod == 1) { // Rotate dimensions (since they change)
            newDimensions = newDimensions.swapXZ();
        }

        int xSize = newDimensions.getX();
        int zSize = newDimensions.getZ();

        CoordinateConverter converter = CoordinateConverter.getConverter(rotation); // Don't worry about it

        int[][][] newArr = new int[dimensions.getY()][xSize][zSize];
        for (int i = 0; i < blocks.length; i++) {
            for (int x = 0; x < dimensions.getX(); x++) {
                for (int z = 0; z < dimensions.getZ(); z++) {
                    CoordinatePair pair = converter.convert(x, z, xSize, zSize);
                    int b = blocks[i][x][z];
                    if(b != -1)
                        b = BlockConverter.rotate(blocks[i][x][z], rotation);
                    newArr[i][pair.x][pair.z] = b;
                }
            }
        }

        Map<IntVector3D, TagCompound> newTiles = tiles.entrySet().stream().map((entry) -> {
            IntVector3D loc = entry.getKey();

            CoordinatePair pair = converter.convert(loc.getX(), loc.getZ(), xSize, zSize);

            return new AbstractMap.SimpleEntry<>(new IntVector3D(pair.x, loc.getY(), pair.z), entry.getValue());
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new Schematic(origin, newDimensions, newArr, newTiles);
    }

    @Override
    public void serialize(GravSerializer serializer) {
        serializer.writeObject(FORMAT_VERSION);

        serializer.writeInt(this.dimensions.getX());
        serializer.writeInt(this.dimensions.getY());
        serializer.writeInt(this.dimensions.getZ());

        serializer.writeInt(this.origin.getX());
        serializer.writeInt(this.origin.getY());
        serializer.writeInt(this.origin.getZ());

        serializer.writeObject(this.blocks);
        serializer.writeObject(this.tiles);
        serializer.writeObject(this.emittedLight);
    }

    @Override
    public String toString() {
        return "Schematic{" +
                "format-version=" + FORMAT_VERSION +
                "origin=" + origin +
                ", dimensions=" + dimensions +
                ", blocks=" + ArrayUtils.toString(blocks) +
                ", squareSize=" + squareSize +
                ", lineSize=" + lineSize +
                '}';
    }

    public interface CoordinateConverter {
        static CoordinateConverter getConverter(int rotation) {
            switch (rotation) {
                case 1:
                    return (x, z, xSize, zSize) -> new CoordinatePair(z, xSize - x - 1);
                case 2:
                    return (x, z, xSize, zSize) -> new CoordinatePair(xSize - x - 1, zSize - z - 1);
                case 3:
                    return (x, z, xSize, zSize) -> new CoordinatePair(zSize - z - 1, x);
                default:
                    throw new IllegalArgumentException("How did we get here??");
            }
        }

        CoordinatePair convert(int x, int z, int xSize, int ySize);
    }

    public Schematic subSchem(IntVector3D min, IntVector3D max) {
        IntVector3D newDimensions = max.subtract(min).add(IntVector3D.ONE);
        int[][][] newblocks = new int[newDimensions.getY()][newDimensions.getX()][newDimensions.getZ()];
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                int[] newI = new int[newDimensions.getZ()];
                System.arraycopy(this.blocks[y][x], min.getZ(), newI, 0, newDimensions.getZ());
                newblocks[y - min.getY()][x - min.getX()] = newI;
            }
        }

        byte[][][] newEmittedLight = new byte[newDimensions.getY()][newDimensions.getX()][newDimensions.getZ()];
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                byte[] newI = new byte[newDimensions.getZ()];
                System.arraycopy(this.emittedLight[y][x], min.getZ(), newI, 0, newDimensions.getZ());
                newEmittedLight[y - min.getY()][x - min.getX()] = newI;
            }
        }

        Map<IntVector3D, TagCompound> newTiles = new HashMap<>();
        tiles.forEach((p, t) -> {
            if (p.getX() >= min.getX() && p.getX() <= max.getX()) {
                if (p.getY() >= min.getY() && p.getY() <= max.getY()) {
                    if (p.getZ() >= min.getZ() && p.getZ() <= max.getZ()) {
                        newTiles.put(p, t);
                    }
                }
            }
        });

        return new Schematic(origin, newDimensions, newblocks, newEmittedLight, newTiles);
    }

    public Schematic subSchemXZ(IntVector2D min, IntVector2D max) {
        return subSchem(IntVector3D.ZERO.addXZ(min), IntVector3D.ZERO.setY(dimensions.getY() - 1).addXZ(max));
    }

    @AllArgsConstructor
    public static class CoordinatePair {
        public int x;
        public int z;
    }

    //Util
    private static byte[][][] castArrayToTripleByte(Object[] in) {
        return Arrays.stream(in).map((obj) -> castArrayToDoubleByte((Object[]) obj)).toArray(byte[][][]::new);
    }

    private static byte[][] castArrayToDoubleByte(Object[] in) {
        Stream var10000 = Arrays.stream(in);
        byte[].class.getClass();
        return (byte[][]) var10000.map(byte[].class::cast).toArray(byte[][]::new);
    }
}
