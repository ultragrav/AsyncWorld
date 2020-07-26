package main.java.net.ultragrav.asyncworld;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChunkLocation {
    private AsyncWorld world;
    private int x;
    private int z;

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof ChunkLocation))
            return false;
        if(o == this)
            return true;
        return getX() == ((ChunkLocation) o).getX() && getZ() == ((ChunkLocation) o).getZ();
    }
}
