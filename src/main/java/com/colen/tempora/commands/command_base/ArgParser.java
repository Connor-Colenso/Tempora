package com.colen.tempora.commands.command_base;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import java.lang.ref.WeakReference;
import java.util.Objects;

public class ArgParser {

    private final String[] args;
    private WeakReference<EntityPlayerMP> playerMP;

    public ArgParser(String[] args, EntityPlayerMP playerWhoIssuedCommand) {
        this.args = args;
        this.playerMP = new WeakReference<>(playerWhoIssuedCommand);
    }

    // Player-aware String argument
    public String getString(int index, IChatComponent errorTranslation) throws CommandException {
        String value = args[index];
        if (value == null || value.isEmpty()) {
            // Send player a message and throw
            Objects.requireNonNull(playerMP.get()).addChatMessage(errorTranslation);
            throw new CommandException("commands.parse.string");
        }
        return value;
    }

    // Player-aware int argument
    public int getInt(int index, IChatComponent errorTranslation) throws CommandException {
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            Objects.requireNonNull(playerMP.get()).addChatMessage(errorTranslation);
            throw new CommandException("commands.parse.int");
        }
    }

    public int getPositiveInteger(int index, IChatComponent errorTranslation) throws CommandException {
        try {
            int i = Integer.parseInt(args[index]);
            if (i < 0) throw new NumberFormatException();
            return i;
        } catch (NumberFormatException e) {
            Objects.requireNonNull(playerMP.get()).addChatMessage(errorTranslation);
            throw new CommandException("commands.parse.int");
        }
    }

    // Player-aware double argument
    public double getDouble(int index, IChatComponent errorTranslation) throws CommandException {
        try {
            return Double.parseDouble(args[index]);
        } catch (NumberFormatException e) {
            Objects.requireNonNull(playerMP.get()).addChatMessage(errorTranslation);
            throw new CommandException("");
        }
    }


    // Check minimum number of arguments
    public boolean minArgs(int required) throws CommandException {
        return args.length >= required;
    }
}
