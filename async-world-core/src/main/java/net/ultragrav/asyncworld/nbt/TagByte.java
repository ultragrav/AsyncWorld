package net.ultragrav.asyncworld.nbt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ultragrav.serializer.GravSerializer;

@AllArgsConstructor
public class TagByte extends Tag {

    @Getter
    private byte data;

    @Override
    public void serialize(GravSerializer gravSerializer) {
        gravSerializer.writeByte(data);
    }

    public static TagByte deserialize(GravSerializer serializer) {
        return new TagByte(serializer.readByte());
    }
}
