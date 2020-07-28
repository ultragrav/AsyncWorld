package net.ultragrav.asyncworld.nbt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.ultragrav.serializer.GravSerializer;

import java.util.List;

@Getter
@AllArgsConstructor
public class TagLong extends Tag {
    private long data;

    @Override
    public void serialize(GravSerializer gravSerializer) {
        gravSerializer.writeObject(data);
    }

    public static TagLong deserialize(GravSerializer serializer) {
        return serializer.readObject();
    }
}
