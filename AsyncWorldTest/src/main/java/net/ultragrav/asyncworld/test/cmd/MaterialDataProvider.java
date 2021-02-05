package net.ultragrav.asyncworld.test.cmd;

import lombok.Getter;
import lombok.NonNull;
import net.ultragrav.command.exception.CommandException;
import net.ultragrav.command.provider.UltraProvider;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

public class MaterialDataProvider extends UltraProvider<Integer> {
    @Getter
    private static final MaterialDataProvider instance = new MaterialDataProvider();

    private MaterialDataProvider() {
    }

    @Override
    public Integer convert(@NonNull String s) throws CommandException {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
        }
        String[] arg = s.split(":");
        if (arg.length > 0) {
            Material mat = Material.matchMaterial(arg[0]);
            if (mat != null) {
                int id = mat.getId();
                int data = 0;
                if (arg.length > 1) {
                    try {
                        data = Integer.parseInt(arg[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                return data << 12 | id;
            }
        }
        throw new CommandException("Expected MaterialData (type:data), got: " + s);
    }

    @Override
    public List<String> tabComplete(@NonNull String s) {
        return Collections.emptyList();
    }

    @Override
    public String getArgumentDescription() {
        return "materialdata";
    }
}
