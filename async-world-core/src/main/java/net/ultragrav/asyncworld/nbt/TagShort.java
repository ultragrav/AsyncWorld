package net.ultragrav.asyncworld.nbt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ultragrav.serializer.GravSerializer;

import java.util.List;

@Getter
@AllArgsConstructor
public class TagShort extends Tag {
    private short data;

    @Override
    public void serialize(GravSerializer gravSerializer) {
        gravSerializer.writeShort(data);
    }

    public static TagShort deserialize(GravSerializer serializer) {
        return new TagShort(serializer.readShort());
    }
}
