package net.ultragrav.asyncworld.customworld;

import net.ultragrav.asyncworld.AsyncWorld;
import net.ultragrav.asyncworld.schematics.Schematic;
import net.ultragrav.utils.IntVector3D;

public abstract class CustomWorldAsyncWorld extends AsyncWorld {
    public abstract CustomWorld getCustomWorld();
    public abstract void pasteSchematic(Schematic schematic, IntVector3D position, boolean ignoreAir);
}
