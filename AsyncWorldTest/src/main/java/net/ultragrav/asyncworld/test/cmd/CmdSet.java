package net.ultragrav.asyncworld.test.cmd;

import net.ultragrav.asyncworld.SpigotAsyncWorld;
import net.ultragrav.asyncworld.test.WorldEditPlayerState;
import net.ultragrav.asyncworld.test.utils.NumberUtils;
import net.ultragrav.utils.CuboidRegion;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CmdSet extends AWCommand {
    public CmdSet() {
        addAlias("set");
        setAllowConsole(false);

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

        service.submit(() -> {
            long ms = System.currentTimeMillis();
            SpigotAsyncWorld world = new SpigotAsyncWorld(region.getWorld());
            long setMs = System.currentTimeMillis();
            world.setBlocks(region, () -> (short) mat);
            setMs = System.currentTimeMillis() - setMs;
            long flushMs = System.currentTimeMillis();
            world.flush(false).thenAccept((Void) -> {
                double time = (System.currentTimeMillis() - ms) / 1000D;
                int blocks = region.getArea();
                tell("§6§lAsyncWorld§8 > &aFilled " + NumberUtils.formatFull(blocks) + " blocks in " + time + "s!");
            });
            flushMs = System.currentTimeMillis() - flushMs;
            tell("§6§lAsyncWorld§8 > &e(" + setMs + ", " + flushMs + ")");
        });
    }
}
