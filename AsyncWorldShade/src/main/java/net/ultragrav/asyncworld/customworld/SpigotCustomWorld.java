package net.ultragrav.asyncworld.customworld;

import lombok.Getter;
import net.ultragrav.asyncworld.AsyncChunk;
import net.ultragrav.asyncworld.SpigotAsyncWorld;
import net.ultragrav.asyncworld.relighter.NMSRelighter;
import net.ultragrav.asyncworld.relighter.Relighter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class SpigotCustomWorld extends CustomWorld {

    private CustomWorldHandler worldHandler;

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

    private SpigotCustomWorldAsyncWorld asyncWorld;

    private final Relighter relighter;

    public ExecutorService service = Executors.newSingleThreadExecutor();

    private final AtomicBoolean startedCreation = new AtomicBoolean(false);

    private final String name;

    private final Plugin plugin;

    @Getter
    private final int sizeChunksX;

    @Getter
    private final int sizeChunksZ;

    private final AtomicBoolean loaded = new AtomicBoolean(false);

    private final AtomicBoolean preloaded = new AtomicBoolean(true);

    private final Map<Long, CustomWorldChunkSnap> currentChunkSnapMap = new ConcurrentHashMap<>();

    /**
     * Create a custom world with a certain size that is generated by a function passed to the
     * function #create that consumes an AsyncWorld instance. #create may, and it is encouraged to, be called
     * asynchronously. #create will almost always take at least 50ms to execute due to scheduling delays, as it
     * does need some operations to happen synchronously, such as calling WorldInitEvent and WorldLoadEvent.
     *
     * @param plugin
     * @param name
     * @param sizeChunksX
     * @param sizeChunksZ
     */
    public SpigotCustomWorld(Plugin plugin, String name, int sizeChunksX, int sizeChunksZ) {
        getServerVersion(); //Make sure it's not null
        this.name = name;
        this.plugin = plugin;
        this.sizeChunksX = sizeChunksX;
        this.sizeChunksZ = sizeChunksZ;
        this.asyncWorld = new SpigotCustomWorldAsyncWorld(this, this.plugin);
        this.relighter = new NMSRelighter(this.plugin);
    }

    /**
     * Create the world
     *
     * @param generator The function that will generate the world's contents
     */
    @Override
    public synchronized void create(Consumer<CustomWorldAsyncWorld> generator) {


        long createWorldMs = -1;

        if (!startedCreation.compareAndSet(false, true))
            throw new RuntimeException("World already created!");

        //Create world server
        if (worldHandler == null) {
            createWorldHandler();
        }
        if (!worldHandler.isWorldCreated()) {
            createWorldMs = System.currentTimeMillis();
            worldHandler.createWorld(this, name);
            createWorldMs = System.currentTimeMillis() - createWorldMs;
        }

        long loadChunkMs = System.currentTimeMillis();
        this.preloaded.set(true);
        for (int x = 0; x < sizeChunksX; x++) {
            for (int z = 0; z < sizeChunksZ; z++) {
                this.asyncWorld.getChunk(x, z);
            }
        }
        loadChunkMs = System.currentTimeMillis() - loadChunkMs;


        //Generate world
        long generateMs = System.currentTimeMillis();
        generator.accept(asyncWorld);
        generateMs = System.currentTimeMillis() - generateMs;

        //Set chunks' sections (write to world)
        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors()); //Multi-threaded
        List<CustomWorldAsyncChunk<?>> chunks = asyncWorld.getChunkMap().getCachedCopy(); //DO NOT CLEAR THE CHUNK MAP because they're 1 time creation chunks
        //that hold the nms chunks
        chunks.forEach((c) -> pool.submit(() -> {
            try {
                worldHandler.finishChunk(c);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        })); //Submit tasks

        long finishChunksMs = System.currentTimeMillis();

        pool.shutdown();
        while (!pool.isTerminated()) {
            try {
                pool.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        finishChunksMs = System.currentTimeMillis() - finishChunksMs;


        //Add to world list (Must be sync)
        long addWorldListMs = System.currentTimeMillis();
        if (!Bukkit.isPrimaryThread()) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            new BukkitRunnable() {
                @Override
                public void run() {
                    worldHandler.addToWorldList();
                    future.complete(null);
                }
            }.runTask(plugin);
            future.join();
        } else {
            worldHandler.addToWorldList();
        }
        addWorldListMs = System.currentTimeMillis() - addWorldListMs;

        queueSkyRelight(chunks); //Relight chunks

        loaded.set(true);
    }

    private String getMask(short s) {
        StringBuilder builder = new StringBuilder();
        for (int i = 15; i >= 0; i--) {
            builder.append(s >>> i & 1);
        }
        return builder.toString();
    }

    @Override
    public synchronized void create(SavedCustomWorld world, boolean preloadChunks) {
        if (!startedCreation.compareAndSet(false, true))
            throw new RuntimeException("World already created!");

        this.preloaded.set(preloadChunks);

        long ms = System.currentTimeMillis();

        Map<Long, CustomWorldChunkSnap> chunkSnapMap = new ConcurrentHashMap<>(); //I don't think this needs to be a concurrent hashmap but just in case

        world.getChunks().forEach(c -> chunkSnapMap.put(((long) c.getX() << 32) | ((long) c.getZ()), c));

        this.currentChunkSnapMap.clear();
        this.currentChunkSnapMap.putAll(chunkSnapMap);

        //Create world server
        if (worldHandler == null)
            createWorldHandler();

        if (!worldHandler.isWorldCreated())
            worldHandler.createWorld(this, name);

        CompletableFuture<Void> addFuture = new CompletableFuture<>();
        AtomicBoolean finishedGeneration = new AtomicBoolean(false);


        //I realize now this is a very overly complicated way of adding to world list, but I'm too lazy to change it
        if (!Bukkit.isPrimaryThread()) {
            AtomicReference<Runnable> r = new AtomicReference<>();
            r.set(() -> {
                if (finishedGeneration.get()) {
                    worldHandler.addToWorldList();
                    addFuture.complete(null);
                } else {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            r.get().run();
                        }
                    }.runTask(plugin);
                }
            });
            new BukkitRunnable() {
                @Override
                public void run() {
                    r.get().run();
                }
            }.runTask(plugin);
        }

        ms = System.currentTimeMillis();

        if (preloadChunks) {

            //Make sure all required chunks are created
            chunkSnapMap.forEach((k, v) -> this.asyncWorld.getChunk(v.getX(), v.getZ()));

            for (int x = 0; x < sizeChunksX; x++) {
                for (int z = 0; z < sizeChunksZ; z++) {
                    this.asyncWorld.getChunk(x, z);
                }
            }

            //

            ms = System.currentTimeMillis();


            ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors()); //Multi-threaded
            List<CustomWorldAsyncChunk<?>> chunks = asyncWorld.getChunkMap().getCachedCopy(); //DO NOT CLEAR THE CHUNK MAP because they're 1 time creation chunks
            //that hold the nms chunks
            chunks.forEach((c) -> pool.submit(() -> {
                CustomWorldChunkSnap snap = chunkSnapMap.get(((long) c.getLoc().getX() << 32) | ((long) c.getLoc().getZ()));
                if (snap != null)
                    c.fromSnap(snap);
                worldHandler.finishChunk(c);
            })); //Submit tasks

            pool.shutdown();
            while (!pool.isTerminated()) {
                try {
                    pool.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }

        finishedGeneration.set(true);

        ms = System.currentTimeMillis();

        //Add to world list (Must be sync)
        if (Bukkit.isPrimaryThread()) {
            worldHandler.addToWorldList();
        } else {
            addFuture.join();
        }

        ms = System.currentTimeMillis();

        loaded.set(true);
    }

    private void queueSkyRelight(List<CustomWorldAsyncChunk<?>> chunks) {
        Map<AsyncChunk, Integer> masks = new HashMap<>();
        for (AsyncChunk chunk : chunks) {
            int editedSections = chunk.getEditedSections();
            for (int i = 0; i < 16; i++) {
                boolean a = ((editedSections >>> i) & 1) != 0;
                if (a && i != 0) {
                    editedSections |= 1 << (i - 1);
                }
                if (a && i != 15) {
                    editedSections |= 1 << (i++ + 1);
                }
            }
            masks.put(chunk, editedSections);
        }

        //Must use a spigot async world because if the relighter creates any new async chunks, it would be bad if that chunk needed
        //finish() to be called because it could be loaded -> it would stall the main thread
        SpigotAsyncWorld spigotAsyncWorld = new SpigotAsyncWorld(getBukkitWorld());

        //Queue
        chunks.forEach(c -> {
            int mask = masks.get(c);

            AsyncChunk spigotAsyncChunk = spigotAsyncWorld.getChunk(c.getLoc().getX(), c.getLoc().getZ());

            Relighter.RelightAction[] actions = new Relighter.RelightAction[16];
            for (int i = 0; i < 16; i++) {
                if ((mask >>> i & 1) == 1) {
                    actions[i] = Relighter.RelightAction.ACTION_RELIGHT;
                } else {
                    actions[i] = Relighter.RelightAction.ACTION_SKIP_AIR;
                }
            }
            relighter.queueSkyRelight(spigotAsyncChunk, actions);
        });
    }

    /**
     * Calls create in a separate thread
     */
    public Future<Void> createAsync(Consumer<CustomWorldAsyncWorld> generator) {
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
    public boolean isWorldCreated() {
        return this.worldHandler != null && this.worldHandler.isWorldCreated();
    }

    public boolean chunkSnapExists(int cx, int cz) {
        return getSnap(cx, cz) != null;
    }

    public CustomWorldChunkSnap getSnap(int cx, int cz) {
        return currentChunkSnapMap.get(((long) cx << 32) | ((long) cz));
    }

    @Override
    public CustomWorldAsyncChunk<?> getChunk(int cx, int cz) {
        if (asyncWorld.getChunkMap().get(cx, cz) == null) {
            if (preloaded.get())
                return null;

            //Load it
            CustomWorldChunkSnap snap = currentChunkSnapMap.get(((long) cx << 32) | ((long) cz));

            //Doesn't exist
            if (snap == null)
                return null;

            CustomWorldAsyncChunk<?> chunk = asyncWorld.getChunk(cx, cz);
            chunk.fromSnap(snap);
            this.worldHandler.finishChunk(chunk);
            currentChunkSnapMap.remove(((long) cx << 32) | ((long) cz));
            return chunk;
        }
        CustomWorldAsyncChunk<?> asyncChunk = asyncWorld.getChunk(cx, cz);
        asyncChunk.awaitFinish();
        return asyncChunk;
    }

    @Override
    public Plugin getPlugin() {
        return this.plugin;
    }

    /**
     * Attempt to unload the world, there must be 0 players currently inside of the world, if there are not 0, you must
     * teleport them out before calling this method
     */
    @Override
    public void unload() {
        this.asyncWorld = new SpigotCustomWorldAsyncWorld(this, this.plugin); //In case something has a reference to the world server, and therefore this object,
        //It's a good idea to dereference the async world and therefore all the chunks inside of it
        if (this.getBukkitWorld() == null) {
            return;
        }
        if (loaded.compareAndSet(true, false)) {
            if (Bukkit.getWorld(this.getBukkitWorld().getUID()) != null) {
                Bukkit.unloadWorld(this.getBukkitWorld(), false);
            }
            this.worldHandler.invalidateWorld(); //Remove world from reference
        }
    }

    @Override
    public SavedCustomWorld getSavedCustomWorld() {
        return save(this.asyncWorld, !loaded.get());
    }

    @Override
    public SavedCustomWorld getSavedCustomWorld(CustomWorldAsyncWorld world) {
        return save((SpigotCustomWorldAsyncWorld) world, !loaded.get());
    }

    @Override
    public CustomWorldAsyncWorld getAsyncWorld() {
        return this.asyncWorld;
    }

    public CustomWorldHandler getWorldHandler() {
        return this.worldHandler;
    }

    /**
     * Saves the world.
     *
     * @param asyncWorld  The world to save.
     * @param asyncIsSafe Whether or not the world is safe to save asynchronously.
     * @return The saved world.
     */
    private SavedCustomWorld save(SpigotCustomWorldAsyncWorld asyncWorld, boolean asyncIsSafe) {
        SavedCustomWorld save = new SavedCustomWorld();

        //Synchronous scheduling.
        AtomicBoolean scheduled = new AtomicBoolean(false);
        Map<Runnable, CompletableFuture<Void>> sync = new HashMap<>();
        ReentrantLock scheduleLock = new ReentrantLock();

        long ms = System.currentTimeMillis();
        ForkJoinPool pool = new ForkJoinPool();
        ReentrantLock lock = new ReentrantLock();

        boolean isPrimaryThread = Bukkit.isPrimaryThread();

        Map<Long, CustomWorldChunkSnap> copiedSnaps = new HashMap<>(this.currentChunkSnapMap);

        Function<Runnable, CompletableFuture<Void>> syncExecutorAsync = (run) -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            scheduleLock.lock();
            try {
                sync.put(run, future);
                if (scheduled.compareAndSet(false, true)) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            scheduled.set(false);
                            //Make a copy of the schedule.
                            scheduleLock.lock();
                            Map<Runnable, CompletableFuture<Void>> copy = new HashMap<>(sync);
                            sync.clear();
                            scheduleLock.unlock();

                            //Run actions.
                            copy.forEach((action, future) -> {
                                try {
                                    action.run();
                                    future.complete(null);
                                } catch (Throwable t) {
                                    future.completeExceptionally(t);
                                }
                            });
                        }
                    }.runTask(plugin);
                }
            } finally {
                scheduleLock.unlock();
            }
            return future;
        };

        for (CustomWorldAsyncChunk<?> chunk : asyncWorld.getChunkMap().getCachedCopy()) {
            pool.submit(() -> {
                try {
                    CustomWorldChunkSnap snap = CustomWorldChunkSnap.fromAsyncChunk(chunk,
                            asyncIsSafe || isPrimaryThread ? (run) -> {
                                run.run();
                                return CompletableFuture.completedFuture(null);
                            } : syncExecutorAsync);

                    lock.lock();
                    save.getChunks().add(snap);
                    lock.unlock();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        }
        pool.shutdown();
        while (true) {
            try {
                if (pool.awaitTermination(1, TimeUnit.SECONDS)) break;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //Wait for the pool to finish.
        }
        ms = System.currentTimeMillis() - ms;

        //Add all of the chunks that have not been loaded yet (if preloading = false)
        if (!preloaded.get()) {
            //Remove chunks that are already in the save.
            for (CustomWorldChunkSnap chunk : save.getChunks()) {
                copiedSnaps.remove(((long) chunk.getX() << 32) | ((long) chunk.getZ()));
            }

            //Add.
            save.getChunks().addAll(copiedSnaps.values());
        }

        return save;
    }
}
