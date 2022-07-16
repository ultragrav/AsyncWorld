package net.ultragrav.asyncworld.test.cmd;

import lombok.Getter;
import lombok.NonNull;
import net.ultragrav.command.exception.CommandException;
import net.ultragrav.command.provider.UltraProvider;
import net.ultragrav.utils.IntVector3D;

import java.util.Collections;
import java.util.List;

public class IntVector3DProvider extends UltraProvider<IntVector3D> {
    @Getter
    private static final IntVector3DProvider instance = new IntVector3DProvider();

    private IntVector3DProvider() {
    }

    @Override
    public IntVector3D convert(@NonNull String s) throws CommandException {
        String[] els = s.split(",");
        if (els.length != 3) {
            throw new CommandException("§cExpected Vector x,y,z but got: " + s);
        }
        int[] ints = new int[3];
        try {
            for (int i = 0; i < 3; i++) {
                ints[i] = Integer.parseInt(els[i]);
            }
        } catch (NumberFormatException e) {
            throw new CommandException("§cExpected Vector x,y,z but got: " + s);
        }
        return new IntVector3D(ints[0], ints[1], ints[2]);
    }

    @Override
    public List<String> tabComplete(@NonNull String s) {
        return Collections.emptyList();
    }

    @Override
    public String getArgumentDescription() {
        return "integer vector";
    }
}
