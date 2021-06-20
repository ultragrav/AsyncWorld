package net.ultragrav.asyncworld.test.cmd;

import net.ultragrav.asyncworld.test.WorldEditPlayerManager;
import net.ultragrav.asyncworld.test.WorldEditPlayerState;
import net.ultragrav.command.platform.SpigotCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class AWCommand extends SpigotCommand {
    protected WorldEditPlayerState getState() {
        return WorldEditPlayerManager.instance.getPlayerState(getPlayer().getUniqueId());
    }

    protected void tell(String message) {
        ((CommandSender) sender.getWrappedObject()).sendMessage(
                ChatColor.translateAlternateColorCodes('&', message)
        );
    }
}
