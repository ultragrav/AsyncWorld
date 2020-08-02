package net.ultragrav.asyncworld.nbt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ultragrav.serializer.GravSerializer;

@Getter
@AllArgsConstructor
public class TagInt extends Tag {

    private int data;

    @Override
    public void serialize(GravSerializer gravSerializer) {
        gravSerializer.writeInt(data);
    }

    public static TagInt deserialize(GravSerializer serializer) {
        return new TagInt(serializer.readInt());
    }
}
