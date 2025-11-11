package com.colen.tempora.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.colen.tempora.loggers.block_change.BlockChangeRecordingRegion;
import com.colen.tempora.loggers.block_change.RegionRegistry;

public class CommandTemporaUndo extends CommandBase {

    @Override
    public String getCommandName() {
        return "tempora_undo";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/tempora_undo event_id";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP‑only by default
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1) throw new WrongUsageException(getCommandUsage(sender));

        ChatComponentTranslation msg = new ChatComponentTranslation(
            "command.tempora.region.created", 1);

        sender.addChatMessage(msg);
    }

}
