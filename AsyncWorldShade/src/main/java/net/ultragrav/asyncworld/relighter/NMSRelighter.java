package net.ultragrav.asyncworld.relighter;

import net.ultragrav.asyncworld.AsyncChunk;
import net.ultragrav.asyncworld.AsyncWorld;
import net.ultragrav.asyncworld.QueuedChunk;
import net.ultragrav.asyncworld.chunk.AsyncChunk1_12_R1;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class NMSRelighter implements Relighter {

    private final Plugin plugin;

    public NMSRelighter(Plugin plugin) {
        this.plugin = plugin;
    }

    private static class QueuedRelight implements Comparable<QueuedRelight> {
        public final AsyncChunk chunk;
        public boolean smooth = false;
        public final RelightAction[] sectionMask;
        public final int[] current = new int[256];

        private QueuedRelight(AsyncChunk chunk, RelightAction[] sectionMask) {
            this.chunk = chunk;
            this.sectionMask = sectionMask;
            Arrays.fill(current, 15);
        }

        @Override
        public int compareTo(QueuedRelight other) {
            int x = chunk.getLoc().getX();
            int z = chunk.getLoc().getZ();
            if (other.chunk.getLoc().getX() < x) {
                return 1;
            }
            if (other.chunk.getLoc().getX() > x) {
                return -1;
            }
            return Integer.compare(z, other.chunk.getLoc().getZ());
        }
    }


    private final List<QueuedRelight> queuedRelights = new ArrayList<>();
    private volatile boolean scheduled = false;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final ExecutorService service = Executors.newSingleThreadExecutor();

    private void schedule() {
        lock.lock();
        try {
            if (scheduled)
                return;
            service.submit(() -> {
                try {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    lock.lock();
                    List<QueuedRelight> relights;
                    try {
                        scheduled = false;
                        relights = new ArrayList<>(queuedRelights);
                        queuedRelights.clear();
                    } finally {
                        lock.unlock();
                    }
                    Collections.sort(queuedRelights);
                    long ms = System.currentTimeMillis();
                    skyRelight(relights);
                    ms = System.currentTimeMillis() - ms;
                    //System.out.println("Relighted in " + ms + "ms");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            scheduled = true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void queueSkyRelight(AsyncChunk chunk, RelightAction[] sectionMask) {
        QueuedRelight relight = new QueuedRelight(chunk, sectionMask);
        for (int i = 0; i < sectionMask.length; i++) {
            if (sectionMask[i] == null)
                sectionMask[i] = RelightAction.ACTION_RELIGHT;
        }
        lock.lock();
        queuedRelights.add(relight);
        lock.unlock();
        schedule();
    }

    @Override
    public void queueRelight(AsyncChunk chunk, RelightAction[] sectionMask) {
        //TODO
    }

    //Credit to boydti (FastAsyncWorldEdit) for much of the below

    private void skyRelight(List<QueuedRelight> chunks) {
        if (chunks.size() != 0) {
            chunks.get(0).chunk.getParent().ensureChunkLoaded(chunks.stream().map(c -> c.chunk).toArray(AsyncChunk[]::new));
        }
        for (int y = 255; y >= 0; y--) {
            for (QueuedRelight queuedChunk : chunks) {
                int[] current = queuedChunk.current;
                int sectionIndex = y >> 4;
                AsyncChunk chunk = queuedChunk.chunk;

                queuedChunk.smooth = false;

                if (!chunk.sectionExists(y >> 4))
                    continue;

                RelightAction[] sectionMask = queuedChunk.sectionMask;

                if (sectionMask[sectionIndex] != RelightAction.ACTION_RELIGHT) {
                    if ((y & 15) == 0 && sectionMask[sectionIndex] == RelightAction.ACTION_SKIP_SOLID) {
                        Arrays.fill(current, (byte) 0);
                    }
                    continue;
                }

                for (int i = 0; i < 256; i++) {
                    int value = current[i];
                    int x = i & 15;
                    int z = i >> 4;
                    int opacity = chunk.syncGetBrightnessOpacity(x, y, z) & 15;
                    switch (value) {
                        case 0:
                            if (opacity > 1) {
                                chunk.syncSetSkyLight(x, y, z, 0);
                                continue;
                            }
                            break;
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                        case 6:
                        case 7:
                        case 8:
                        case 9:
                        case 10:
                        case 11:
                        case 12:
                        case 13:
                        case 14:
                            if (opacity >= value) {
                                current[i] = 0;
                                chunk.syncSetSkyLight(x, y, z, 0);
                                continue;
                            }
                            if (opacity <= 1) {
                                current[i] = --value;
                            } else {
                                current[i] = value = Math.max(0, value - opacity);
                            }
                            break;
                        case 15:
                            if (opacity > 1) {
                                value -= opacity;
                                current[i] = value;
                            }
                            chunk.syncSetSkyLight(x, y, z, value);
                            continue;
                    }
                    queuedChunk.smooth = true;

                    chunk.syncSetSkyLight(x, y, z, value);
                }
            }
            for (QueuedRelight chunk1 : chunks) { // Smooth forwards
                if (chunk1.smooth) {
                    smoothSkyLight(chunk1, y, true);
                }
            }
            for (int i = chunks.size() - 1; i >= 0; i--) { // Smooth backwards
                QueuedRelight chunk1 = chunks.get(i);
                if (chunk1.smooth) {
                    smoothSkyLight(chunk1, y, false);
                }
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                chunks.forEach(c -> c.chunk.sendPackets(0xFFFF)); //Send chunks
            }
        }.runTask(this.plugin);
    }

    private int getOpacity(AsyncChunk chunk, int x, int y, int z) {
        return chunk.syncGetBrightnessOpacity(x, y, z) & 15;
    }

    private int getSkyLightForRelighting(AsyncWorld world, int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        AsyncChunk chunk = world.getChunk(cx, cz);
        int skyLight = world.syncGetSkyLight(x, y, z);
        if (skyLight != 0)
            return skyLight;
        if (!chunk.sectionExists(y >> 4)) {
            return 15;
        }
        return 0;
    }

    private void smoothSkyLight(QueuedRelight chunk, int y, boolean direction) {
        int[] mask = chunk.current;
        final int bx = chunk.chunk.getLoc().getX() << 4;
        final int bz = chunk.chunk.getLoc().getZ() << 4;

        if (direction) {
            for (int j = 0; j < 256; j++) {
                int x = j & 15;
                int z = j >> 4;
                if (mask[j] >= 14 || (mask[j] == 0 && getOpacity(chunk.chunk, x, y, z) > 1)) {
                    continue;
                }
                int value = mask[j];
                if ((value = Math.max(getSkyLightForRelighting(chunk.chunk.getParent(), bx + x - 1, y, bz + z) - 1, value)) >= 14)
                    ;
                else if ((value = Math.max(getSkyLightForRelighting(chunk.chunk.getParent(), bx + x, y, bz + z - 1) - 1, value)) >= 14)
                    ;
                if (value > mask[j]) chunk.chunk.syncSetSkyLight(x, y, z, mask[j] = value);
            }
        } else {
            for (int j = 255; j >= 0; j--) {
                int x = j & 15;
                int z = j >> 4;
                if (mask[j] >= 14 || (mask[j] == 0 && getOpacity(chunk.chunk, x, y, z) > 1)) {
                    continue;
                }
                int value = mask[j];
                if ((value = (byte) Math.max(getSkyLightForRelighting(chunk.chunk.getParent(), bx + x + 1, y, bz + z) - 1, value)) >= 14)
                    ;
                else if ((value = (byte) Math.max(getSkyLightForRelighting(chunk.chunk.getParent(), bx + x, y, bz + z + 1) - 1, value)) >= 14)
                    ;
                if (value > mask[j]) chunk.chunk.syncSetSkyLight(x, y, z, mask[j] = value);
            }
        }
    }
}
