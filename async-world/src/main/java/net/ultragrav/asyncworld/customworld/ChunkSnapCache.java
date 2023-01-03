package net.ultragrav.asyncworld.customworld;

import net.ultragrav.serializer.GravSerializer;
import net.ultragrav.serializer.compressors.StandardCompressor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compressed cache of chunk snapshots
 */
public class ChunkSnapCache {
    private final Map<Long, byte[]> cache = new ConcurrentHashMap<>();

    public CustomWorldChunkSnap get(int x, int z) {
        byte[] data = cache.get(getKey(x, z));
        if (data == null) {
            return null;
        }
        // Decompress data
        byte[] decompressed = StandardCompressor.instance.decompress(data);
        return new CustomWorldChunkSnap(new GravSerializer(decompressed), x, z);
    }

    public void put(int x, int z, CustomWorldChunkSnap snap) {
        GravSerializer serializer = new GravSerializer();
        snap.serialize(serializer);
        byte[] compressed = StandardCompressor.instance.compress(serializer.toByteArray());
        cache.put(getKey(x, z), compressed);
    }

    public void remove(int x, int z) {
        cache.remove(getKey(x, z));
    }

    public boolean contains(int cx, int cz) {
        return cache.containsKey(getKey(cx, cz));
    }

    public void load(List<CustomWorldChunkSnap> list) {
        for (CustomWorldChunkSnap snap : list) {
            put(snap.getX(), snap.getZ(), snap);
        }
    }

    public Map<Long, CustomWorldChunkSnap> save() {
        Map<Long, CustomWorldChunkSnap> map = new ConcurrentHashMap<>(cache.size());
        for (Map.Entry<Long, byte[]> entry : cache.entrySet()) {
            int x = (int) (entry.getKey() >> 32);
            int z = (int) (entry.getKey() & 0xFFFFFFFFL);
            map.put(entry.getKey(), get(x, z));
        }
        return map;
    }

    public void clear() {
        cache.clear();
    }

    public static long getKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
