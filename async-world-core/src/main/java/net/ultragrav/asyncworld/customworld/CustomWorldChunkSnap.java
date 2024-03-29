package net.ultragrav.asyncworld.customworld;

import lombok.Getter;
import lombok.Setter;
import net.ultragrav.asyncworld.AsyncChunk;
import net.ultragrav.asyncworld.chunk.NextTickEntry;
import net.ultragrav.asyncworld.schematics.Schematic;
import net.ultragrav.nbt.wrapper.TagCompound;
import net.ultragrav.nbt.wrapper.TagInt;
import net.ultragrav.serializer.GravSerializable;
import net.ultragrav.serializer.GravSerializer;
import net.ultragrav.utils.IntVector3D;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class CustomWorldChunkSnap implements GravSerializable {

    private static final int VERSION = 2;

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
    private final List<NextTickEntry> nextTickEntries;
    private final short sectionBitMask;

    public CustomWorldChunkSnap(int[] heightMap, byte[][] emittedLight, byte[][] skyLight, byte[][] blocks, byte[][] blockData, byte[] biomes, List<TagCompound> entities, List<TagCompound> tiles, List<NextTickEntry> nextTickEntries, short sectionBitMask) {
        this.heightMap = heightMap;
        this.emittedLight = emittedLight;
        this.skyLight = skyLight;
        this.blocks = blocks;
        this.blockData = blockData;
        this.biomes = biomes;
        this.entities = entities;
        this.tiles = tiles;
        this.nextTickEntries = nextTickEntries;
        this.sectionBitMask = sectionBitMask;
    }

    public static byte[][] getBlocksInternal(CustomWorldChunkSnap snap) {
        return snap.blocks;
    }

    //Block data.
    public static byte[][] getBlockDataInternal(CustomWorldChunkSnap snap) {
        return snap.blockData;
    }

    //Sky light.
    public static byte[][] getSkyLightInternal(CustomWorldChunkSnap snap) {
        return snap.skyLight;
    }

    //Emitted light.
    public static byte[][] getEmittedLightInternal(CustomWorldChunkSnap snap) {
        return snap.emittedLight;
    }

    //Height map.
    public static int[] getHeightMapInternal(CustomWorldChunkSnap snap) {
        return snap.heightMap;
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
            if (skyLight[i] != null) {
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

    public List<TagCompound> getEntitiesInternal() {
        return entities;
    }

    public List<TagCompound> getTiles() {
        return new ArrayList<>(tiles);
    }

    public List<TagCompound> getTilesInternal() {
        return tiles;
    }

    public List<NextTickEntry> getNextTickEntries() {
        return new ArrayList<>(nextTickEntries);
    }

    public List<NextTickEntry> getNextTickEntriesInternal() {
        return nextTickEntries;
    }

    public short getSectionBitMask() {
        return sectionBitMask;
    }

    public CustomWorldChunkSnap(GravSerializer serializer, int x, int z) {
        int version = serializer.readInt();
        this.heightMap = serializer.readObject();
        this.biomes = serializer.readObject();

        if (version < 2) {
            this.tiles = serializer.readObject();
            this.entities = serializer.readObject();
            this.nextTickEntries = new ArrayList<>();
        } else {
            this.tiles = new ArrayList<>();
            this.entities = new ArrayList<>();
            this.nextTickEntries = new ArrayList<>();

            int tileCount = serializer.readInt();
            for (int i = 0; i < tileCount; i++) {
                this.tiles.add(TagCompound.deserialize(serializer));
            }

            int entityCount = serializer.readInt();
            for (int i = 0; i < entityCount; i++) {
                this.entities.add(TagCompound.deserialize(serializer));
            }

            int nextTickCount = serializer.readInt();
            for (int i = 0; i < nextTickCount; i++) {
                this.nextTickEntries.add(NextTickEntry.deserialize(serializer));
            }

        }

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

        serializer.writeInt(this.tiles.size());
        for (TagCompound tile : this.tiles) {
            tile.serialize(serializer);
        }

        serializer.writeInt(this.entities.size());
        for (TagCompound entity : this.entities) {
            entity.serialize(serializer);
        }

        serializer.writeInt(this.nextTickEntries.size());
        for (NextTickEntry entry : this.nextTickEntries) {
            entry.serialize(serializer);
        }

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

    public Schematic toSchematic() {
        Schematic schematic = new Schematic(new IntVector3D(0, 0, 0), new IntVector3D(16, 256, 16));
        for (int i = 0; i < blockData.length; i++) {
            if (blockData[i] == null) {
                continue;
            }
            for (int j = 0; j < blockData[i].length; j++) {
                int x = j & 0xF;
                int y = (j >> 8) + (i << 4);
                int z = (j >> 4) & 0xF;
                int block = blocks[i][j] & 0xFF;
                int data = blockData[i][j] >> ((j & 1) << 2) & 0xF;
                int combined = (block << 4) | data;
                combined &= 0xFFF;
                schematic.setBlockAt(x, y, z, combined);

                int emitted = emittedLight[i][j] >> ((j & 1) << 2);
                schematic.setEmittedLightAt(x, y, z, (byte) (emitted & 0xF));
            }
        }
        tiles.forEach(value -> {
            int x = ((TagInt) value.getData().get("x")).getData();
            int y = ((TagInt) value.getData().get("y")).getData();
            int z = ((TagInt) value.getData().get("z")).getData();
            schematic.getTiles().put(new IntVector3D(x, y, z), value);
        });
        return schematic;
    }

    private static String getMask(short s) {
        StringBuilder builder = new StringBuilder();
        for (int i = 15; i >= 0; i--) {
            builder.append(s >>> i & 1);
        }
        return builder.toString();
    }

    public static CustomWorldChunkSnap fromAsyncChunk(AsyncChunk chunk, Function<Runnable, CompletableFuture<Void>> syncExecutor) {
        try {
            //Sync
            AtomicReference<List<TagCompound>> tiles = new AtomicReference<>();
            AtomicReference<List<TagCompound>> entities = new AtomicReference<>();
            AtomicReference<List<NextTickEntry>> nextTickEntries = new AtomicReference<>();

            byte[][] blocks = new byte[16][], blockData = new byte[16][];
            short sectionBitMask = chunk.getSectionBitMask();

            Runnable runnable = () -> {
                tiles.set(new ArrayList<>(chunk.syncGetTiles().values()));
                entities.set(chunk.syncGetEntities());
                nextTickEntries.set(Arrays.asList(chunk.syncGetNextTickEntries()));

                for (int i = 0; i < 16; i++) {
                    if (((sectionBitMask >>> i) & 1) == 0)
                        continue;
                    byte[] blocksS = blocks[i] = new byte[4096];
                    byte[] blockDataS = blockData[i] = new byte[2048];
                    chunk.syncGetBlocksAndData(blocksS, blockDataS, i);
                }
            };

            CompletableFuture<Void> syncFuture = new CompletableFuture<>();
            if (Bukkit.isPrimaryThread()) {
                runnable.run();
                syncFuture.complete(null);
            } else {
                syncFuture = syncExecutor.apply(runnable);
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

            syncFuture.join();
            CustomWorldChunkSnap snap = new CustomWorldChunkSnap(heightMap, emittedLight, skyLight, blocks, blockData, biomes, entities.get(), tiles.get(), nextTickEntries.get(), sectionBitMask);
            snap.setX(chunk.getLoc().getX());
            snap.setZ(chunk.getLoc().getZ());
            return snap;
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }
}
