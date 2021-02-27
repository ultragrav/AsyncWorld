package net.ultragrav.asyncworld.test.cmd;

import net.ultragrav.asyncworld.test.WorldEditPlayerManager;
import net.ultragrav.asyncworld.test.WorldEditPlayerState;
import net.ultragrav.command.platform.SpigotCommand;

public class AWCommand extends SpigotCommand {
    protected WorldEditPlayerState getState() {
        return WorldEditPlayerManager.instance.getPlayerState(getPlayer().getUniqueId());
    }
}
