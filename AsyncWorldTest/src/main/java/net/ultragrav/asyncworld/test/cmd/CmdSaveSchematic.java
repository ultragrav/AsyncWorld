package net.ultragrav.asyncworld.test.cmd;

import net.ultragrav.asyncworld.SpigotAsyncWorld;
import net.ultragrav.asyncworld.schematics.Schematic;
import net.ultragrav.asyncworld.test.AWTest;
import net.ultragrav.asyncworld.test.WorldEditPlayerManager;
import net.ultragrav.asyncworld.test.WorldEditPlayerState;
import net.ultragrav.command.UltraCommand;
import net.ultragrav.command.provider.impl.StringProvider;
import net.ultragrav.utils.CuboidRegion;
import net.ultragrav.utils.IntVector3D;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

public class CmdSaveSchematic extends AWCommand {
    public CmdSaveSchematic() {
        this.addAlias("saveschem");
        this.addAlias("saveschematic");

        this.setAllowConsole(false);

        this.addParameter(StringProvider.getInstance(), "file name");
        this.addParameter(IntVector3D.ZERO, IntVector3DProvider.getInstance(), "origin");
        this.addParameter(-1, MaterialDataProvider.getInstance(), "ignore block");
    }

    public void perform() {
        String name = getArgument(0);
        IntVector3D origin = getArgument(1);
        int ignoredBlock = getArgument(2);

        File f = new File(AWTest.instance.getDataFolder(), "schematics/" + name + ".bschem");
        if (f.getParentFile() != null)
            f.getParentFile().mkdirs();
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        WorldEditPlayerState state = getState();

        CuboidRegion region = new CuboidRegion(state.getPos1(), state.getPos2());

        Schematic schem = new Schematic(origin, new SpigotAsyncWorld(region.getWorld()), region, ignoredBlock);
        try {
            schem.save(f);
            tell("&6&lAsyncWorld&8 > &aSuccess! Custom Origin: " + origin.getX() + " " + origin.getY() + " " + origin.getZ() + (ignoredBlock != -1 ? " ignoredBlock: " + args.get(2) + " (" + ignoredBlock + ")" : ""));
        } catch (IOException e) {
            e.printStackTrace();
            tell("&6&lAsyncWorld&8 > &cShit happened during the process of attempting to try to saving your schematic");
        }
    }
}
