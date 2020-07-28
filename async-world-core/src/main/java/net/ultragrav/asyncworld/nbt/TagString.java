package net.ultragrav.asyncworld.nbt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ultragrav.serializer.GravSerializer;

@AllArgsConstructor
@Getter
public class TagString extends Tag {

    private String data;

    @Override
    public void serialize(GravSerializer gravSerializer) {
        gravSerializer.writeObject(data);
    }

    public TagString deserialize(GravSerializer serializer) {
        return serializer.readObject();
    }
}
