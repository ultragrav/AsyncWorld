package net.ultragrav.asyncworld.nbt;

import net.ultragrav.serializer.GravSerializer;

public class TagEnd extends Tag {

    @Override
    public void serialize(GravSerializer gravSerializer) {
    }

    public static TagEnd deserialize(GravSerializer serializer) {
        return new TagEnd();
    }
}
