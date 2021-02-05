package net.ultragrav.asyncworld.test;

import net.ultragrav.asyncworld.test.utils.EventSubscription;
import net.ultragrav.asyncworld.test.utils.EventSubscriptions;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorldEditPlayerManager {
    public static WorldEditPlayerManager instance;

    private AWTest parent;

    private Map<UUID, WorldEditPlayerState> playerStateMap = new HashMap<>();

    public WorldEditPlayerManager(AWTest parent) {
        instance = this;
        this.parent = parent;
        EventSubscriptions.instance.subscribe(this);
    }

    public WorldEditPlayerState getPlayerState(UUID id) {
        if (!playerStateMap.containsKey(id)) {
            playerStateMap.put(id, new WorldEditPlayerState(id));
        }
        return playerStateMap.get(id);
    }

    public ItemStack getWand() {
        return new ItemStack(Material.WOOD_SPADE);
    }

    public boolean isWand(ItemStack item) {
        return item != null && item.getType() == Material.WOOD_SPADE;
    }

    @EventSubscription
    public void onInteract(PlayerInteractEvent e) {
        if (!e.getPlayer().hasPermission("asyncworld.worldedit")) {
            return;
        }
        if (!isWand(e.getItem())) {
            return;
        }
        e.setCancelled(true);
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            getPlayerState(e.getPlayer().getUniqueId()).setPos1(e.getClickedBlock().getLocation());
            e.getPlayer().sendMessage("§6§lAsyncWorld§8 > §aPosition 1 set");
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            getPlayerState(e.getPlayer().getUniqueId()).setPos2(e.getClickedBlock().getLocation());
            e.getPlayer().sendMessage("§6§lAsyncWorld§8 > §aPosition 2 set");
        }
    }
}
