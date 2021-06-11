package net.ultragrav.asyncworld.customworld;

import lombok.Getter;
import lombok.Setter;
import net.ultragrav.asyncworld.AsyncChunk;
import net.ultragrav.asyncworld.nbt.TagCompound;
import net.ultragrav.serializer.GravSerializable;
import net.ultragrav.serializer.GravSerializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class CustomWorldChunkSnap implements GravSerializable {

    private static final int VERSION = 1;

    @Getter
    @Setter
    private int x = 0;
    @Getter
    @Setter
    private int z = 0;
    private final int[] heightMap;
    private final byte[][] emittedLight;
    private final byte[][] skyLight;
    private final byte[][] blocks;
    private final byte[][] blockData;
    private final byte[] biomes;
    private final List<TagCompound> entities;
    private final List<TagCompound> tiles;
    private final short sectionBitMask;

    public CustomWorldChunkSnap(int[] heightMap, byte[][] emittedLight, byte[][] skyLight, byte[][] blocks, byte[][] blockData, byte[] biomes, List<TagCompound> entities, List<TagCompound> tiles, short sectionBitMask) {
        this.heightMap = heightMap;
        this.emittedLight = emittedLight;
        this.skyLight = skyLight;
        this.blocks = blocks;
        this.blockData = blockData;
        this.biomes = biomes;
        this.entities = entities;
        this.tiles = tiles;
        this.sectionBitMask = sectionBitMask;
    }

    public int[] getHeightMap() {
        return Arrays.copyOf(heightMap, heightMap.length);
    }

    public byte[][] getEmittedLight() {
        byte[][] emitted = new byte[emittedLight.length][];
        for (int i = 0; i < emitted.length; i++) {
            if (emittedLight[i] != null) {
                emitted[i] = Arrays.copyOf(emittedLight[i], emittedLight[i].length);
            }
        }
        return emitted;
    }

    public byte[][] getSkyLight() {
        byte[][] sky = new byte[skyLight.length][];
        for (int i = 0; i < sky.length; i++) {
            if (emittedLight[i] != null) {
                sky[i] = Arrays.copyOf(skyLight[i], skyLight[i].length);
            }
        }
        return sky;
    }

    public byte[][] getBlocks() {
        byte[][] blocksArr = new byte[this.blocks.length][];
        for (int i = 0; i < blocksArr.length; i++) {
            if (this.blocks[i] != null) {
                blocksArr[i] = Arrays.copyOf(this.blocks[i], this.blocks[i].length);
            }
        }
        return blocksArr;
    }

    public byte[][] getBlockData() {
        byte[][] blocks = new byte[this.blockData.length][];
        for (int i = 0; i < blocks.length; i++) {
            if (this.blockData[i] != null) {
                blocks[i] = Arrays.copyOf(this.blockData[i], this.blockData[i].length);
            }
        }
        return blocks;
    }

    public byte[] getBiomes() {
        return Arrays.copyOf(biomes, biomes.length);
    }

    public List<TagCompound> getEntities() {
        return new ArrayList<>(entities);
    }

    public List<TagCompound> getTiles() {
        return new ArrayList<>(tiles);
    }

    public short getSectionBitMask() {
        return sectionBitMask;
    }

    public CustomWorldChunkSnap(GravSerializer serializer, int x, int z) {
        int version = serializer.readInt();
        this.heightMap = serializer.readObject();
        this.biomes = serializer.readObject();
        this.tiles = serializer.readObject();
        this.entities = serializer.readObject();
        this.sectionBitMask = serializer.readShort();

        this.blocks = new byte[16][];
        this.blockData = new byte[16][];
        this.emittedLight = new byte[16][];
        this.skyLight = new byte[16][];

        for (int i = 0; i < 16; i++) {
            if (((this.sectionBitMask >>> i) & 1) == 0)
                continue;

            //Section deserialization
            this.blocks[i] = serializer.readObject();
            this.blockData[i] = serializer.readObject();
            this.emittedLight[i] = serializer.readObject();
            this.skyLight[i] = serializer.readObject();
        }

        this.x = x;
        this.z = z;
    }

    @Override
    public void serialize(GravSerializer serializer) {
        serializer.writeInt(VERSION);
        serializer.writeObject(this.heightMap);
        serializer.writeObject(this.biomes);
        serializer.writeObject(this.tiles);
        serializer.writeObject(this.entities);
        serializer.writeShort(this.sectionBitMask);
        for (int i = 0; i < 16; i++) {
            if (((this.sectionBitMask >>> i) & 1) == 0)
                continue;

            //Section serialization
            serializer.writeObject(this.blocks[i]); //Blocks
            serializer.writeObject(this.blockData[i]); //Block data
            serializer.writeObject(this.emittedLight[i]); //Emitted light
            serializer.writeObject(this.skyLight[i]); //Sky light
        }
    }

    private static String getMask(short s) {
        StringBuilder builder = new StringBuilder();
        for(int i = 15; i >= 0; i--) {
            builder.append(s >>> i & 1);
        }
        return builder.toString();
    }

    public static CustomWorldChunkSnap fromAsyncChunk(AsyncChunk chunk, Function<Runnable, CompletableFuture<Void>> syncExecutor) {
        try {
            //Sync
            AtomicReference<List<TagCompound>> tiles = new AtomicReference<>();
            AtomicReference<List<TagCompound>> entities = new AtomicReference<>();

            byte[][] blocks = new byte[16][], blockData = new byte[16][];
            short sectionBitMask = chunk.getSectionBitMask();

            Runnable runnable = () -> {
                tiles.set(new ArrayList<>(chunk.syncGetTiles().values()));
                entities.set(chunk.syncGetEntities());

                for (int i = 0; i < 16; i++) {
                    if (((sectionBitMask >>> i) & 1) == 0)
                        continue;
                    byte[] blocksS = blocks[i] = new byte[4096];
                    byte[] blockDataS = blockData[i] = new byte[2048];
                    chunk.syncGetBlocksAndData(blocksS, blockDataS, i);
                }
            };

            CompletableFuture<Void> f = new CompletableFuture<>();
            if (Bukkit.isPrimaryThread()) {
                runnable.run();
                f.complete(null);
            } else {
                f = syncExecutor.apply(runnable);
            }

            //Possibly async
            int[] heightMap = chunk.syncGetHeightMap();
            byte[] biomes = chunk.syncGetBiomes();
            byte[][] emittedLight = new byte[16][], skyLight = new byte[16][];
            for (int i = 0; i < 16; i++) {
                if (((sectionBitMask >>> i) & 1) == 0)
                    continue;

                emittedLight[i] = chunk.syncGetEmittedLight(i);
                skyLight[i] = chunk.syncGetSkyLight(i);
            }

            f.join();
            CustomWorldChunkSnap snap = new CustomWorldChunkSnap(heightMap, emittedLight, skyLight, blocks, blockData, biomes, entities.get(), tiles.get(), sectionBitMask);
            snap.setX(chunk.getLoc().getX());
            snap.setZ(chunk.getLoc().getZ());
            return snap;
        } catch(Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }
}
