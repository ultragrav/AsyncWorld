package net.ultragrav.asyncworld.test;

import lombok.Data;
import net.ultragrav.asyncworld.schematics.Schematic;
import org.bukkit.Location;

import java.util.UUID;

@Data
public class WorldEditPlayerState {
    private final UUID player;
    private Location pos1;
    private Location pos2;
    private Schematic clipboard;
}
