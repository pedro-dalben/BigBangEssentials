package com.pedrodalben.bigbangessentials.shop.handlers;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/** Loader-neutral sign text operations used by both platform adapters. */
public final class ShopSignText {
    private ShopSignText() {}

    public static String[] read(SignBlockEntity sign) {
        String[] lines = new String[4];
        var signText = sign.getFrontText();
        for (int i = 0; i < lines.length; i++) {
            lines[i] = signText.getMessage(i, false).getString();
        }
        return lines;
    }

    public static void write(ServerLevel level, BlockPos pos, String[] lines) {
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
        for (int i = 0; i < 4 && i < lines.length; i++) {
            Component component = Component.literal(lines[i] != null ? lines[i] : "");
            var newText = sign.getFrontText().setMessage(i, component);
            sign.updateText(s -> newText, true);
        }
        sign.setChanged();
        var state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }
}
