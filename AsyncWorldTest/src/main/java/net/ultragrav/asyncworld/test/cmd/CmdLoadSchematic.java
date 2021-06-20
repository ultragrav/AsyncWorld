package net.ultragrav.asyncworld.test.cmd;

import net.ultragrav.asyncworld.schematics.Schematic;
import net.ultragrav.asyncworld.test.AWTest;
import net.ultragrav.asyncworld.test.WorldEditPlayerManager;
import net.ultragrav.asyncworld.test.WorldEditPlayerState;
import net.ultragrav.command.UltraCommand;
import net.ultragrav.command.platform.SpigotCommand;
import net.ultragrav.command.provider.impl.StringProvider;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class CmdLoadSchematic extends AWCommand {
    public CmdLoadSchematic() {
        addAlias("loadschem");

        setAllowConsole(false);

        addParameter(StringProvider.getInstance(), "schematic");
    }

    @Override
    public void perform() {
        if (!sender.hasPermission("asyncworld.loadschem")) {
            tell("§6§lAsyncWorld§8 > &cYou don't have permission to do this!");
            return;
        }
        Player player = getSpigotPlayer();

        String name = getArgument(0);
        File f = new File(AWTest.instance.getDataFolder(), "schematics/" + name + ".bschem");
        if (!f.exists()) {
            tell("§cThat schematic does not exist");
            return;
        }

        WorldEditPlayerState state = getState();

        try {
            Schematic schem = new Schematic(new FileInputStream(f), null);
            state.setClipboard(schem);
            tell("§aSchematic loaded successfully!");
        } catch (IOException e) {
            tell("§cThe schematic could not be loaded:");
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                tell("§c" + stackTraceElement.toString());
            }
        }
    }
}
