package net.ultragrav.asyncworld.test.cmd;

import net.ultragrav.asyncworld.test.WorldEditPlayerManager;
import net.ultragrav.command.platform.SpigotCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CmdWand extends AWCommand {
    public CmdWand() {
        this.addAlias("wand");
        this.setAllowConsole(false);
    }

    public void perform() {
        Player player = getSpigotPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(WorldEditPlayerManager.instance.getWand());
        player.getInventory().addItem(stack);
    }
}
