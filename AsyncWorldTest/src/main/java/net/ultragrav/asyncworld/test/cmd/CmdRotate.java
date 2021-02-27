package net.ultragrav.asyncworld.test.cmd;

import net.ultragrav.asyncworld.test.WorldEditPlayerManager;
import net.ultragrav.asyncworld.test.WorldEditPlayerState;
import net.ultragrav.command.UltraCommand;
import net.ultragrav.command.provider.impl.IntegerProvider;
import org.bukkit.entity.Player;

public class CmdRotate extends AWCommand {
    public CmdRotate() {
        this.addAlias("rotate");

        this.setAllowConsole(false);

        this.addParameter(IntegerProvider.getInstance(), "rotation (0-3)");
    }

    public void perform() {
        int rot = getArgument(0);

        WorldEditPlayerState state = getState();

        state.setClipboard(state.getClipboard().rotate(rot));

        tell("§aRotated!");
    }
}
