package net.ultragrav.asyncworld.customworld;

import net.ultragrav.serializer.GravSerializable;
import net.ultragrav.serializer.GravSerializer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SavedCustomWorld implements GravSerializable {

    public static final int VERSION = 2;

    private GravSerializer extra = new GravSerializer();

    private final List<CustomWorldChunkSnap> chunks = new ArrayList<>();

    public SavedCustomWorld() {
    }

    public SavedCustomWorld(GravSerializer serializer) {
        int version = serializer.readInt();

        if (version < 2) {
            int baseX = serializer.readInt();
            int baseZ = serializer.readInt();
            int sizeX = serializer.readInt();
            int sizeZ = serializer.readInt();

            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    CustomWorldChunkSnap snap = new CustomWorldChunkSnap(serializer, x + baseX, z + baseZ);
                    this.chunks.add(snap);
                }
            }
        } else {
            int size = serializer.readInt();
            for (int i = 0; i < size; i++) {
                CustomWorldChunkSnap snap = new CustomWorldChunkSnap(serializer, serializer.readInt(), serializer.readInt());
                this.chunks.add(snap);
            }
        }

        this.extra = serializer.readSerializer();
    }

    @Override
    public void serialize(GravSerializer serializer) {

        //Version
        serializer.writeInt(VERSION);

        //Chunks
        serializer.writeInt(this.chunks.size());
        for (CustomWorldChunkSnap snap : this.chunks) {
            serializer.writeInt(snap.getX());
            serializer.writeInt(snap.getZ());
            snap.serialize(serializer);
        }

        //Extra
        serializer.writeSerializer(this.extra);
    }

    public GravSerializer getExtra() {
        return this.extra;
    }

    public List<CustomWorldChunkSnap> getChunks() {
        return this.chunks;
    }

    /**
     * Move all chunks by some amount
     * @param x The amount of chunks (16 blocks) to translate in the X direction
     * @param z The amount of chunks (16 blocks) to translate in the Z direction
     */
    public void translate(int x, int z) {
        this.chunks.forEach(c -> {
            c.setX(c.getX() + x);
            c.setZ(c.getZ() + z);
        });
    }
}
