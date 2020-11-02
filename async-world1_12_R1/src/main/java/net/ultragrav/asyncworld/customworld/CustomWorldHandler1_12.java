package net.ultragrav.asyncworld.customworld;

import net.minecraft.server.v1_12_R1.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_12_R1.CraftServer;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

public class CustomWorldHandler1_12 implements CustomWorldHandler {

    private static Field craftBukkitWorldMap;

    static {
        try {
            craftBukkitWorldMap = CraftServer.class.getDeclaredField("worlds");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private CustomWorldServer1_12 world = null;

    private static final ReentrantLock safetyLock = new ReentrantLock(true);

    @Override
    public void finishChunk(CustomWorldAsyncChunk<?> chunk) {
        ((CustomWorldAsyncChunk1_12) chunk).finish(world);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void createWorld(CustomWorld customWorld, String name) {
        CustomWorldDataManager1_12 dataManager = new CustomWorldDataManager1_12(customWorld);

        //This lock is to prevent two threads from editing the craftBukkitWorldMap at the same time
        //As well as preventing concurrent world.b() calls
        safetyLock.lock();

        int dimension;
        //Instantiating world calls bukkitServer.addWorld(this)
        //So we change that map to a synchronized map
        try {
            craftBukkitWorldMap.setAccessible(true);
            Map<Object, Object> current = (Map<Object, Object>) craftBukkitWorldMap.get(Bukkit.getServer());
            if (!current.getClass().getName().contains("Synchronized")) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                new BukkitRunnable() {
                    public void run() {
                        try {
                            craftBukkitWorldMap.setAccessible(true);
                            craftBukkitWorldMap.set(Bukkit.getServer(), Collections.synchronizedMap((Map<Object, Object>) craftBukkitWorldMap.get(Bukkit.getServer())));
                            craftBukkitWorldMap.setAccessible(false);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        } finally {
                            future.complete(null);
                        }
                    }
                }.runTask(customWorld.getPlugin());
                future.get();
            }
            craftBukkitWorldMap.setAccessible(false);

            dimension = CraftWorld.CUSTOM_DIMENSION_OFFSET + Bukkit.getServer().getWorlds().size();
            boolean used = true;

            while (used) {
                for (World server : Bukkit.getServer().getWorlds()) { //Using bukkit getWorlds because that uses a concurrent map (was set to it earlier ^) and is *hopefully* safe
                    used = ((CraftWorld) server).getHandle().dimension == dimension;

                    if (used) {
                        dimension++;
                        break;
                    }
                }
            }
            safetyLock.unlock();
            world = new CustomWorldServer1_12(dataManager, dimension); //Instantiating world calls bukkitServer.addWorld(this)
            safetyLock.lock();
            world.b();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            safetyLock.unlock();
        }
    }

    private final ReentrantLock addLock = new ReentrantLock(true);

    @Override
    public void addToWorldList() {
        if (world == null) {
            throw new IllegalArgumentException("World object must be an instance of WorldServer!");
        }

        MinecraftServer mcServer = MinecraftServer.getServer();

        addLock.lock(); // Not necessary but just makes me feel better
        try {
            if (mcServer.server.getWorld(world.getWorld().getUID()) == null) {
                mcServer.server.addWorld(world.getWorld());
            }
            if (!mcServer.worlds.contains(world)) {
                mcServer.worlds.add(world);
            }
        } finally {
            addLock.unlock();
        }

        //NOTE: It would seem calling these is necessary for certain spigot functions to work in this world
        //The one I encountered was falling blocks not caring for setDropItem(false)
        Bukkit.getPluginManager().callEvent(new WorldInitEvent(world.getWorld()));
        Bukkit.getPluginManager().callEvent(new WorldLoadEvent(world.getWorld()));
    }

    @Override
    public synchronized boolean isWorldCreated() {
        return world != null;
    }

    @Override
    public synchronized World getBukkitWorld() {
        return world == null ? null : world.getWorld();
    }

    @Override
    public void invalidateWorld() {
        this.world = null;
    }
}
