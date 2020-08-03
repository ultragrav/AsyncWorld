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
        int data = block >>> 12;
        Material mat = Material.getMaterial(id);
        if (mat.name().contains("RAIL")) {
            if (data == 0 || data == 1)
                data = rotateData2((byte) data);
            else if (data > 5)
                data = ((data-6) + 3 & 3) + 6;
            else {
                if(data == 4)
                    data = 5;
                else if(data == 5)
                    data = 4;
                data = rotateData4((byte) (data - 2)) + 2;
                if(data == 4)
                    data = 5;
                else if(data == 5)
                    data = 4;
            }
        } else if (mat.name().endsWith("FENCE_GATE")) {
            data = rotateData2((byte) data);
        } else if (mat.name().contains("TRAP_DOOR")) {
            data = rotateData4Inverse((byte) data);
        } else if (mat.name().contains("STAIRS") || mat.name().contains("VINE")
                || mat.name().contains("LADDER") || mat.name().contains("DOOR")) {
            data = rotateData4((byte) data);
        } else if (mat.name().contains("PISTON") || mat.name().contains("CHEST")) {
            if (data != 0 && data != 1)
                data = rotateData4Inverse((byte) (data - 2)) + 2;
        } else if (mat.name().contains("TORCH")) {
            if (data != 1)
                data = rotateData4((byte) (data - 1)) + 1;
        }
        return data << 12 | id;
    }

    private static byte rotateData4(byte data) {
        byte ab = (byte) (data & 0x3);
        byte a = (byte) (~(ab >>> 1) & 1);
        byte b = (byte) (ab & 1);
        byte result = (byte) (a << 1 | a ^ b);
        return (byte) ((data & ~0x3) | result);
    }

    protected static byte rotateData4Inverse(byte data) {
        byte ab = (byte) (data & 0x3);
        ab = (byte) (0x3 - ab);
        byte a = (byte) (~(ab >>> 1) & 1);
        byte b = (byte) (ab & 1);
        byte result = (byte) (a << 1 | a ^ b);
        return (byte) ((data & ~0x3) | (0x3 - result));
    }

    private static byte rotateData2(byte data) {
        return (byte) (data & ~1 | (~data & 1));
    }
}
