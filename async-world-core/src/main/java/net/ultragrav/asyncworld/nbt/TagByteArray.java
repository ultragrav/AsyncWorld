package net.ultragrav.asyncworld.nbt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ultragrav.serializer.GravSerializer;

@AllArgsConstructor
public class TagByteArray extends Tag {
    @Getter
    private byte[] data;

    @Override
    public void serialize(GravSerializer gravSerializer) {
        gravSerializer.writeByteArray(data);
    }

    public static TagByteArray deserialize(GravSerializer serializer) {
        return new TagByteArray(serializer.readByteArray());
    }
}
