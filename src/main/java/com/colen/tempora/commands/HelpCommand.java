package com.colen.tempora.commands;

import static com.colen.tempora.utils.CommandUtils.OP_ONLY;

import java.util.List;
import java.util.Map;

import com.colen.tempora.commands.command_base.CommandArg;
import com.colen.tempora.commands.command_base.TemporaCommandBase;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public class HelpCommand extends TemporaCommandBase {

    public HelpCommand() {
        super(
            new CommandArg("<command_name>", "tempora.command.help.help.arg0")
        );
    }

    @Override
    public String getCommandName() {
        return "tempora_help";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return OP_ONLY; // OP only
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        // Syntax check
        if (args.length < 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        Map<String, ICommand> commandMap = MinecraftServer.getServer()
            .getCommandManager()
            .getCommands();
        ICommand command = commandMap.get(args[0]);

        if (command == null) {
            ChatComponentTranslation invalidCommand = new ChatComponentTranslation(
                "tempora.command.help.command_not_found",
                args[0]);
            sender.addChatMessage(invalidCommand);
            return;
        }

        if (command instanceof TemporaCommandBase temporaCommand) {
            IChatComponent descriptionSeparator = new ChatComponentText("-----------------------------------------------------");
            descriptionSeparator.getChatStyle().setColor(EnumChatFormatting.DARK_GRAY);
            sender.addChatMessage(descriptionSeparator);

            // Description: %s.
            {
                IChatComponent description = temporaCommand.getCommandDescription();
                sender.addChatMessage(new ChatComponentTranslation("tempora.command.help.label.description", description));
            }

            // Usage: %s.
            {
                IChatComponent commandUsageLabel = new ChatComponentTranslation("tempora.command.help.label.usage", temporaCommand.getCommandUsage(null));
                sender.addChatMessage(commandUsageLabel);
            }

            // <x1> <x2> etc : Description
            {
                for (IChatComponent argAndDescription : temporaCommand.generateCommandArgsWithDescriptions()) {
                    sender.addChatMessage(argAndDescription);
                }
            }

            // Example usage
            // Example: /tempora_help <command_name>

        } else {
            sender
                .addChatMessage(new ChatComponentTranslation("tempora.command.help.command_not_from_tempora", args[0]));
        }
    }

    private List<String> completeTemporaCommandNames(String[] args) {
        List<String> temporaCommandNames = new java.util.ArrayList<>();

        Map<String, ICommand> commandMap = MinecraftServer.getServer()
            .getCommandManager()
            .getCommands();

        for (ICommand command : commandMap.values()) {
            if (command instanceof TemporaCommandBase temporaCommand) {
                temporaCommandNames.add(temporaCommand.getCommandName());
            }
        }

        return CommandBase.getListOfStringsMatchingLastWord(args, temporaCommandNames.toArray(new String[0]));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return completeTemporaCommandNames(args);
    }

    @Override
    public String getExampleArgs() {
        return "tempora_explode";
    }

    @Override
    public String getTranslationKeyBase() {
        return "tempora.command.help";
    }

    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.help.help.description");
    }
}
