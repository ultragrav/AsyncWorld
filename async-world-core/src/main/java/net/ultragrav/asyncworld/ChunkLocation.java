package net.ultragrav.asyncworld;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@Getter
@AllArgsConstructor
public class ChunkLocation {
    private final AsyncWorld world;
    private final int x;
    private final int z;

    @Override
    public int hashCode() {
        return Objects.hash(world.getBukkitWorld().getUID().toString(), x, z);
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof ChunkLocation))
            return false;
        if(o == this)
            return true;
        return getX() == ((ChunkLocation) o).getX() && getZ() == ((ChunkLocation) o).getZ() && world.getBukkitWorld().getUID().equals(((ChunkLocation) o).getWorld().getBukkitWorld().getUID());
    }
}
