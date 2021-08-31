package net.ultragrav.asyncworld.customworld;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

public class CustomWorldChunkGenerator1_12 extends ChunkGenerator {
    private static final byte[] EMPTY = new byte[16*16*256];

    public byte[] generate(World world, Random random, int x, int z) {
        return EMPTY;
    }
}
