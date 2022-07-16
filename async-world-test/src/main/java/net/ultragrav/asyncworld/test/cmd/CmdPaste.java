package net.ultragrav.asyncworld.test.cmd;

import net.ultragrav.asyncworld.AsyncWorld;
import net.ultragrav.asyncworld.SpigotAsyncWorld;
import net.ultragrav.asyncworld.schematics.Schematic;
import net.ultragrav.asyncworld.test.WorldEditPlayerManager;
import net.ultragrav.asyncworld.test.WorldEditPlayerState;
import net.ultragrav.command.UltraCommand;
import net.ultragrav.utils.IntVector3D;
import org.bukkit.entity.Player;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CmdPaste extends AWCommand {
    public CmdPaste() {
        this.addAlias("paste");

        this.setAllowConsole(false);
    }

    private final ExecutorService service = Executors.newCachedThreadPool((r) -> new Thread(r, "AsyncWorld CmdPaste"));

    @Override
    public void perform() {
        if (!sender.hasPermission("asyncworld.paste")) {
            tell("§6§lAsyncWorld§8 > &cYou don't have permission to do this!");
            return;
        }
        Player player = (Player) sender.getWrappedObject();

        WorldEditPlayerState state = getState();
        Schematic schem = state.getClipboard();

        //service.submit(() -> {
            AsyncWorld world = new SpigotAsyncWorld(player.getLocation().getWorld());
            long ms = System.currentTimeMillis();
            world.pasteSchematic(schem, IntVector3D.fromBukkitVector(player.getLocation().toVector()));
            player.sendMessage("§6§lAsyncWorld§8 > Pasted in memory in " + ((System.currentTimeMillis() - ms) / 1000D) + "s");
            if(!world.syncFlush(2000)) {
                System.out.println("Big Problem!!!!");
            }
//            .thenAccept((vo) -> {
//                double time = (System.currentTimeMillis() - ms) / 1000D;
//                player.sendMessage("§6§lAsyncWorld§8 > Pasted in " + time + "s");
//            });
            player.sendMessage("§6§lAsyncWorld§8 > Flushed in memory in " + ((System.currentTimeMillis() - ms) / 1000D) + "s");
        //});
    }
}
