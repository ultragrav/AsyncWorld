package net.ultragrav.asyncworld.chunk;

import net.ultragrav.serializer.GravSerializable;
import net.ultragrav.serializer.GravSerializer;

public class NextTickEntry implements GravSerializable {
    private String blockId;
    private int x;
    private int y;
    private int z;
    private long delay;
    private int priority;

    public NextTickEntry(String blockId, int x, int y, int z, long delay, int priority) {
        this.blockId = blockId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.priority = priority;
        this.delay = delay;
    }

    public String getBlockId() {
        return blockId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getPriority() {
        return priority;
    }

    public long getDelay() {
        return delay;
    }

    @Override
    public void serialize(GravSerializer gravSerializer) {
        gravSerializer.writeString(blockId);
        gravSerializer.writeInt(x);
        gravSerializer.writeInt(y);
        gravSerializer.writeInt(z);
        gravSerializer.writeLong(delay);
        gravSerializer.writeInt(priority);
    }

    public static NextTickEntry deserialize(GravSerializer gravSerializer) {
        String blockId = gravSerializer.readString();
        int x = gravSerializer.readInt();
        int y = gravSerializer.readInt();
        int z = gravSerializer.readInt();
        long time = gravSerializer.readLong();
        int priority = gravSerializer.readInt();
        return new NextTickEntry(blockId, x, y, z, time, priority);
    }
}
