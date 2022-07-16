package net.ultragrav.asyncworld.test.cmd;

import net.ultragrav.asyncworld.SpigotAsyncWorld;
import net.ultragrav.asyncworld.schematics.Schematic;
import net.ultragrav.asyncworld.test.WorldEditPlayerManager;
import net.ultragrav.asyncworld.test.WorldEditPlayerState;
import net.ultragrav.command.UltraCommand;
import net.ultragrav.utils.CuboidRegion;
import net.ultragrav.utils.IntVector3D;
import org.bukkit.entity.Player;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CmdCopy extends AWCommand {
    public CmdCopy() {
        addAlias("copy");
        setAllowConsole(false);
    }

    private ExecutorService service = Executors.newSingleThreadExecutor();

    public void perform() {
        if (!sender.hasPermission("asyncworld.copy")) {
            tell("&6&lAsyncWorld&8 > &cYou don't have permission to do this!");
            return;
        }
        if (!isPlayer()) {
            tell("&6&lAsyncWorld&8 > &cYou must be a player to use this command");
            return;
        }

        WorldEditPlayerState state = getState();

        if (state.getPos1() == null || state.getPos2() == null) {
            tell("&6&lAsyncWorld&8 > &7Please make a valid selection!");
            return;
        }

        CuboidRegion region = new CuboidRegion(state.getPos1(), state.getPos2());
        IntVector3D origin = new IntVector3D(0, 0, 0);

        service.submit(() -> {
            long ms = System.currentTimeMillis();
            Schematic schem = new SpigotAsyncWorld(region.getWorld()).optimizedCreateSchematic(region, origin, -1);
            double time = (System.currentTimeMillis() - ms) / 1000D;
            state.setClipboard(schem);
            tell("&6&lAsyncWorld&8 > &aCopied in " + time + "s!");
        });
    }
}
