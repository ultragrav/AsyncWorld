package net.ultragrav.asyncworld.customworld;

import net.ultragrav.asyncworld.AsyncChunk;
import net.ultragrav.asyncworld.AsyncWorld;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class SpigotCustomWorld extends CustomWorld {

    private CustomWorldHandler1_12 worldHandler;

    private static int sV;

    private static String SERVER_VERSION = null;

    public static String getServerVersion() {
        if (SERVER_VERSION == null) {
            String pathName = Bukkit.getServer().getClass().getName();
            String[] parts = pathName.split("\\.");
            SERVER_VERSION = parts[3];
        }
        if (SERVER_VERSION.startsWith("v1_12"))
            sV = 1;
        else
            sV = -1;
        return SERVER_VERSION;
    }

    public static int getServerVersionInt() {
        return sV;
    }

    private final CustomWorldAsyncWorld asyncWorld = new CustomWorldAsyncWorld();

    public ExecutorService service = Executors.newSingleThreadExecutor();

    private final AtomicBoolean startedCreation = new AtomicBoolean(false);

    private final String name;

    private final Plugin plugin;

    private final int sizeChunksX;
    private final int sizeChunksZ;

    public SpigotCustomWorld(Plugin plugin, String name, int sizeChunksX, int sizeChunksZ) {
        getServerVersion(); //Make sure it's not null
        this.name = name;
        this.plugin = plugin;
        this.sizeChunksX = sizeChunksX;
        this.sizeChunksZ = sizeChunksZ;
    }

    public void create(Consumer<AsyncWorld> generator) {

        if (!startedCreation.compareAndSet(false, true))
            throw new RuntimeException("World already created!");

        //Create world server
        if (worldHandler == null)
            createWorldHandler();
        if (!worldHandler.isWorldCreated())
            worldHandler.createWorld(this, name);

        //Generate world
        generator.accept(asyncWorld);

        //Set chunks' sections (write to world)
        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors()); //Multi-threaded
        asyncWorld.getChunkMap().getCachedCopy().forEach((c) -> pool.submit(() -> worldHandler.finishChunk(c))); //Submit tasks
        while (!pool.isQuiescent()) pool.awaitQuiescence(1, TimeUnit.SECONDS); //Wait for tasks to complete

        //Add to world list
        worldHandler.addToWorldList();
    }

    public Future<Void> createAsync(Consumer<AsyncWorld> generator) {
        return service.submit(() -> {
            create(generator);
            return null;
        });
    }

    private void createWorldHandler() {
        if (sV == 1) {
            this.worldHandler = new CustomWorldHandler1_12();
        } else {
            throw new RuntimeException("Server version not supported!");
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public World getBukkitWorld() {
        return this.worldHandler.getBukkitWorld();
    }

    @Override
    public CustomWorldAsyncChunk<?> getChunk(int cx, int cz) {
        if (cx < 0 || cz < 0 || cx > sizeChunksX || cz > sizeChunksZ) {
            return null;
        }
        if (asyncWorld.getChunkMap().get(cx, cz) == null)
            return null;
        CustomWorldAsyncChunk<?> asyncChunk = asyncWorld.getChunk(cx, cz);
        asyncChunk.awaitFinish();
        return asyncChunk;
    }

    @Override
    public Plugin getPlugin() {
        return this.plugin;
    }

    @Override
    public void unload() {
        if(this.getBukkitWorld() == null)
            return;
        Bukkit.unloadWorld(this.getBukkitWorld(), false);
    }
}
