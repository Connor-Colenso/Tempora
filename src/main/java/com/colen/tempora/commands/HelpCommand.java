package com.colen.tempora.commands;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.commands.command_base.TemporaSubcommandGroup;

// /tempora help [command]
// Lists every command or shows one in detail, e.g. /tempora help region set
public class HelpCommand extends TemporaSubcommand {

    private final TemporaSubcommandGroup root;

    public HelpCommand(TemporaSubcommandGroup root) {
        super("help", "[command]");
        this.root = root;
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            root.sendCommandList(sender);
            return;
        }

        TemporaSubcommand command = root.find(args);
        if (command == null || command.isHidden()) {
            sendError(sender, "tempora.command.unknown", String.join(" ", args));
            return;
        }

        if (command instanceof TemporaSubcommandGroup group) {
            group.sendCommandList(sender);
            return;
        }

        command.sendHelp(sender);
    }

    // Completes command names one level at a time, e.g. "region" then "set"
    @Override
    public List<String> tabComplete(ICommandSender sender, String[] args) {
        TemporaSubcommand parent = root.find(Arrays.copyOf(args, args.length - 1));
        if (!(parent instanceof TemporaSubcommandGroup group)) return null;

        return CommandBase.getListOfStringsFromIterableMatchingLastWord(args, group.getVisibleChildNames());
    }

    @Override
    public String getExampleArgs() {
        return "region set";
    }
}
