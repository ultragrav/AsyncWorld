package net.ultragrav.asyncworld.schematics;

import org.bukkit.Bukkit;
import org.bukkit.Material;

public class BlockConverter {
    public static int rotate(int block, int rot) {
        int t = block;
        for (int i = 0; i < rot; i++) {
            t = rotate(t);
        }
        return t;
    }

    public static int rotate(int block) {
        int id = block & 0xFFF;
        int data = block >> 12;
        Material mat = Material.getMaterial(id);
        Bukkit.broadcastMessage("Mapping data: " + data);
        if (mat.name().endsWith("STAIRS")) {
            if (data == 0) {
                data = 3;
            }
            if (data == 1) {
                data = 2;
            }
            if (data == 2) {
                data = 0;
            }
            if (data == 3) {
                data = 1;
            }
        }
        if (mat.name().endsWith("FENCE_GATE")) {
            data = ((data - 1) & 4) | (data & ~0x8);
        }
        Bukkit.broadcastMessage("  to " + data);
        return data << 12 | id;
    }
}
