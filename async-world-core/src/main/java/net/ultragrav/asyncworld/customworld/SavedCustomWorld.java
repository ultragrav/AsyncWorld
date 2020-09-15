package net.ultragrav.asyncworld.customworld;

import net.ultragrav.serializer.GravSerializable;
import net.ultragrav.serializer.GravSerializer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SavedCustomWorld implements GravSerializable {

    public static final int VERSION = 1;

    private GravSerializer extra = new GravSerializer();

    private final List<CustomWorldChunkSnap> chunks = new ArrayList<>();

    private final int sizeX;
    private final int sizeZ;

    public SavedCustomWorld(int sizeX, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
    }

    public SavedCustomWorld(GravSerializer serializer) {
        int version = serializer.readInt();
        int baseX = serializer.readInt();
        int baseZ = serializer.readInt();
        this.sizeX = serializer.readInt();
        this.sizeZ = serializer.readInt();

        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                CustomWorldChunkSnap snap = new CustomWorldChunkSnap(serializer, x + baseX, z + baseZ);
                this.chunks.add(snap);
            }
        }

        this.extra = serializer.readSerializer();
    }

    @Override
    public void serialize(GravSerializer serializer) {

        //Order chunks
        this.chunks.sort(Comparator.comparingLong(c -> ((long) c.getX() << 32) | ((long) c.getZ())));

        int baseX = 0;
        int baseZ = 0;
        if (this.chunks.size() != 0) {
            baseX = this.chunks.get(0).getX();
            baseZ = this.chunks.get(0).getZ();
        }

        //Version
        serializer.writeInt(VERSION);

        //Header
        serializer.writeInt(baseX);
        serializer.writeInt(baseZ);
        serializer.writeInt(sizeX);
        serializer.writeInt(sizeZ);

        //Chunks
        this.getChunks().forEach(c -> c.serialize(serializer));

        //Extra
        serializer.writeSerializer(extra);
    }

    public GravSerializer getExtra() {
        return this.extra;
    }

    public List<CustomWorldChunkSnap> getChunks() {
        return this.chunks;
    }
}
