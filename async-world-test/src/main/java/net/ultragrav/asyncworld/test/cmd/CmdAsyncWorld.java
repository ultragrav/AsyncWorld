package net.ultragrav.asyncworld.test.cmd;

import net.ultragrav.command.UltraCommand;

public class CmdAsyncWorld extends UltraCommand {
    public CmdAsyncWorld() {
        addAlias("asyncworld");

        addChildren(
                new CmdCopy(),
                new CmdLoadSchematic(),
                new CmdPaste(),
                new CmdWand(),
                new CmdSaveSchematic(),
                new CmdRotate(),
                new CmdSet(),
                new CmdReplace()
        );
    }

    @Override
    protected void perform() {

    }
}
