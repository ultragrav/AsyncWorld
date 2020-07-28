package net.ultragrav.asyncworld.nbt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ultragrav.serializer.GravSerializer;

@AllArgsConstructor
@Getter
public class TagFloat extends Tag {

    private float data;

    @Override
    public void serialize(GravSerializer gravSerializer) {
        gravSerializer.writeFloat(data);
    }

    public static TagFloat deserialize(GravSerializer serializer) {
        return new TagFloat(serializer.readFloat());
    }
}
