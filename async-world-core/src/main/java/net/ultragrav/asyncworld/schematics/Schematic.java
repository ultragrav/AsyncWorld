package net.ultragrav.asyncworld.schematics;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ultragrav.asyncworld.AsyncWorld;
import net.ultragrav.asyncworld.nbt.TagCompound;
import net.ultragrav.serializer.GravSerializable;
import net.ultragrav.serializer.GravSerializer;
import net.ultragrav.utils.*;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Schematic implements GravSerializable {

    private static final int FORMAT_VERSION = 1;

    private final IntVector3D origin;
    private final IntVector3D dimensions;
    private final int[][][] blocks;
    @Getter
    private Map<IntVector3D, TagCompound> tiles = new HashMap<>();
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
        this.dimensions = serializer.readObject();
        this.origin = serializer.readObject();
        blocks = ArrayUtils.castArrayToTripleInt(serializer.readObject());
        if (formatVersion > 0) {
            tiles = serializer.readObject();
        }
        squareSize = dimensions.getY() * dimensions.getZ();
        lineSize = dimensions.getZ();
    }

    public Schematic(File file) throws IOException {
        this(new GravSerializer(new FileInputStream(file), true));
    }

    //TODO tile entities for rotate and subSchem
    public Schematic(IntVector3D origin, IntVector3D dimensions, int[][][] blocks) {
        this.origin = origin;
        this.dimensions = dimensions;
        this.blocks = blocks;
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
        tiles = new HashMap<>(copy.tiles); //Shallow copy, changes to nbt data in copied schematic will affect ones in this schematic -> may change later
    }

    public Schematic(IntVector3D origin, AsyncWorld world, CuboidRegion region) {
        this.origin = origin;
        this.dimensions = region.getMaximumPoint().subtract(region.getMinimumPoint()).add(Vector3D.ONE).asIntVector();
        this.blocks = new int[region.getHeight()][region.getWidth()][region.getLength()];

        IntVector3D min = region.getMinimumPoint().asIntVector();

        squareSize = dimensions.getX() * dimensions.getZ();
        lineSize = dimensions.getZ();

        world.syncForAllInRegion(region, (loc, block, tag) -> {
            IntVector3D relLoc = loc.asIntVector().subtract(min);

            blocks[relLoc.getY()][relLoc.getX()][relLoc.getZ()] = block;

            if (tag != null) {
                tiles.put(relLoc, tag);
            }
        }, true);
    }

    public int getBlockAt(Vector3D relLoc) {
        return getBlockAt(relLoc.getBlockY(), relLoc.getBlockX(), relLoc.getBlockZ());
    }

    public int getBlockAt(int x, int y, int z) {
        return this.blocks[y][x][z];
    }

    public IntVector3D getDimensions() {
        return this.dimensions;
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
        serializer.writeToStream(new FileOutputStream(file), true);
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
            for (int x = 0; x < xSize; x++) {
                for (int z = 0; z < zSize; z++) {
                    CoordinatePair pair = converter.convert(x, z, xSize, zSize);
                    newArr[i][pair.x][pair.z] = blocks[i][x][z];
                }
            }
        }
        return new Schematic(origin, newDimensions, newArr);
    }

    @Override
    public void serialize(GravSerializer serializer) {
        serializer.writeObject(FORMAT_VERSION);
        serializer.writeObject(this.dimensions);
        serializer.writeObject(this.origin);
        serializer.writeObject(this.blocks);
        serializer.writeObject(this.tiles);
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
        return new Schematic(origin, newDimensions, newblocks);
    }

    public Schematic subSchemXZ(IntVector2D min, IntVector2D max) {
        return subSchem(IntVector3D.ZERO.addXZ(min), IntVector3D.ZERO.setY(dimensions.getY() - 1).addXZ(max));
    }

    @AllArgsConstructor
    public static class CoordinatePair {
        public int x;
        public int z;
    }
}
