package net.ultragrav.asyncworld.nbt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ultragrav.serializer.GravSerializer;

import java.util.List;

@Getter
@AllArgsConstructor
public class TagLongArray extends Tag {
    private long[] data;

    @Override
    public void serialize(GravSerializer gravSerializer) { //writeObject does not support long[]
        gravSerializer.writeInt(data.length);
        for(long l : data) {
            gravSerializer.writeLong(l);
        }
    }

    public static TagLongArray deserialize(GravSerializer serializer) {
        long[] data = new long[serializer.readInt()];
        for(int i = 0; i < data.length; i++) {
            data[i] = serializer.readLong();
        }
        return new TagLongArray(data);
    }
}
