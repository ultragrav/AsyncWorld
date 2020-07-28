package net.ultragrav.asyncworld.nbt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ultragrav.serializer.GravSerializer;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class TagList extends Tag {
    private List<Tag> data;

    @Override
    public void serialize(GravSerializer gravSerializer) {
        gravSerializer.writeObject(data);
    }

    public static TagList deserialize(GravSerializer serializer) {
        return serializer.readObject();
    }
}
