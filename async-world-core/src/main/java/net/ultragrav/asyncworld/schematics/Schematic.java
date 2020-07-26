package main.java.net.ultragrav.asyncworld.schematics;

import lombok.AllArgsConstructor;
import net.ultragrav.asyncworld.*;
import net.ultragrav.serializer.GravSerializable;
import net.ultragrav.serializer.GravSerializer;
import net.ultragrav.utils.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class Schematic implements GravSerializable {
    private final IntVector3D origin;
    private final IntVector3D dimensions;
    private final int[][][] blocks;
    private final int squareSize;
    private final int lineSize;

    public Schematic(GravSerializer serializer) {
        this.dimensions = serializer.readObject();
        this.origin = serializer.readObject();
        blocks = ArrayUtils.castArrayToTripleInt(serializer.readObject());
        squareSize = dimensions.getY() * dimensions.getZ();
        lineSize = dimensions.getZ();
    }

    public Schematic(File file) throws IOException {
        this(new GravSerializer(new FileInputStream(file), true));
    }

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
    }

    public Schematic(IntVector3D origin, CuboidRegion region) {
        this.origin = origin;
        this.dimensions = region.getMaximumPoint().subtract(region.getMinimumPoint()).add(Vector3D.ONE).asIntVector();
        AsyncWorld world = new SpigotAsyncWorld(region.getWorld());
        this.blocks = new int[region.getHeight()][region.getWidth()][region.getLength()];

        Vector3D min = region.getMinimumPoint();

        squareSize = dimensions.getX() * dimensions.getZ();
        lineSize = dimensions.getZ();

        world.syncForAllInRegion(region, (loc, block) -> {
            Vector3D relLoc = loc.subtract(min);
            blocks[relLoc.getBlockY()][relLoc.getBlockX()][relLoc.getBlockZ()] = block;
        }, true);
    }

    public int getBlockAt(Vector3D relLoc) {
        return getBlockAt(relLoc.getBlockY(), relLoc.getBlockX(), relLoc.getBlockZ());
    }

    public int getBlockAt(int x, int y, int z) {
        //System.out.println("Schematic.getBlockAt(" + x + ", " + y + ", " + z + ")");
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
        serializer.writeObject(this.dimensions);
        serializer.writeObject(this.origin);
        serializer.writeObject(this.blocks);
    }

    @Override
    public String toString() {
        return "Schematic{" +
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
        for (int y = min.getY(); y <= max.getY(); y ++) {
            for (int x = min.getX(); x <= max.getX(); x ++) {
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
