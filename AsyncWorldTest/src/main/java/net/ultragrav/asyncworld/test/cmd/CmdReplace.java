package net.ultragrav.asyncworld.test.cmd;

import net.ultragrav.asyncworld.SpigotAsyncWorld;
import net.ultragrav.asyncworld.test.WorldEditPlayerManager;
import net.ultragrav.asyncworld.test.WorldEditPlayerState;
import net.ultragrav.asyncworld.test.utils.NumberUtils;
import net.ultragrav.command.UltraCommand;
import net.ultragrav.utils.CuboidRegion;
import org.bukkit.entity.Player;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CmdReplace extends AWCommand {
    public CmdReplace() {
        addAlias("replace");
        setAllowConsole(false);

        addParameter(MaterialDataProvider.getInstance());
        addParameter(MaterialDataProvider.getInstance());
    }

    private ExecutorService service = Executors.newSingleThreadExecutor();

    public void perform() {
        WorldEditPlayerState state = getState();

        if (state.getPos1() == null || state.getPos2() == null) {
            returnTell("§6§lAsyncWorld§8 > Please make a valid selection!");
        }

        CuboidRegion region = new CuboidRegion(state.getPos1(), state.getPos2());
        int mat = getArgument(0);
        int repl = getArgument(1);

        service.submit(() -> {
            long ms = System.currentTimeMillis();
            SpigotAsyncWorld world = new SpigotAsyncWorld(region.getWorld());
            AtomicInteger blocks = new AtomicInteger();
            world.asyncForAllInRegion(region, (loc, id, tagCompound, light) -> {
                if (id == mat) {
                    world.setBlock(loc, repl & 4095, (byte)(repl >>> 12));
                    blocks.incrementAndGet();
                }
            }, true);
            world.flush().thenAccept((Void) -> {
                double time = (System.currentTimeMillis() - ms) / 1000D;
                tell("§6§lAsyncWorld§8 > &aReplaced " + NumberUtils.formatFull(blocks.get()) + " blocks in " + time + "s!");
            });
        });
    }
}
