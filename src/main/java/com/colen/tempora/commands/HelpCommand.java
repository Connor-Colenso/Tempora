package com.colen.tempora.commands;

import static com.colen.tempora.utils.ChatUtils.createCenteredLine;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.commands.command_base.TemporaSubcommandGroup;

/**
 * /tempora help [command]
 * Lists every command, or shows the details of one, e.g. /tempora help region set.
 */
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

        // E.g. ----------- region set -----------
        sender.addChatMessage(createCenteredLine(command.getPath()));

        sender.addChatMessage(labelled("tempora.command.help.label.description", command.getDescription()));
        sender.addChatMessage(labelled("tempora.command.help.label.usage", new ChatComponentText(command.getUsage())));

        String exampleArgs = command.getExampleArgs();
        sender.addChatMessage(
            labelled(
                "tempora.command.help.label.example",
                new ChatComponentText(exampleArgs.isEmpty() ? command.command() : command.command(exampleArgs))));

        for (IChatComponent argument : command.getArgumentDescriptions()) {
            sender.addChatMessage(argument);
        }
    }

    // E.g. "Usage: ..." with the label in yellow and the value in white.
    private static IChatComponent labelled(String labelKey, IChatComponent value) {
        value.getChatStyle()
            .setColor(EnumChatFormatting.WHITE);

        IChatComponent line = new ChatComponentTranslation(labelKey, value);
        line.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        return line;
    }

    // Completes command names one level at a time, e.g. "region" then "set".
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
