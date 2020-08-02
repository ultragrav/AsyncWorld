package net.ultragrav.asyncworld.nbt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ultragrav.serializer.GravSerializer;

@Getter
@AllArgsConstructor
public class TagIntArray extends Tag {

    private int[] data;

    @Override
    public void serialize(GravSerializer gravSerializer) {
        gravSerializer.writeObject(data);
    }

    public static TagIntArray deserialize(GravSerializer serializer) {
        return new TagIntArray(serializer.readObject());
    }
}
