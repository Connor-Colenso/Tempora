package com.colen.tempora.commands;

import static com.colen.tempora.utils.CommandUtils.OP_ONLY;

import java.util.List;
import java.util.Map;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.TemporaCommandBase;

public class HelpCommand extends TemporaCommandBase {

    @Override
    public String getCommandName() {
        return "tempora_help";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/tempora_help <command_name>";
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
            IChatComponent descriptionSeparator = new ChatComponentText(
                "-----------------------------------------------------");
            descriptionSeparator.getChatStyle()
                .setColor(EnumChatFormatting.DARK_GRAY);

            IChatComponent commandUsageLabel = new ChatComponentTranslation("tempora.command.help.label.usage");
            commandUsageLabel.getChatStyle()
                .setUnderlined(true);
            commandUsageLabel.getChatStyle()
                .setColor(EnumChatFormatting.AQUA);

            IChatComponent commandUsage = temporaCommand.getFormattedCommand();

            IChatComponent commandDescriptionLabel = new ChatComponentTranslation(
                "tempora.command.help.label.description");
            commandDescriptionLabel.getChatStyle()
                .setUnderlined(true);
            commandDescriptionLabel.getChatStyle()
                .setColor(EnumChatFormatting.AQUA);

            IChatComponent commandArgumentsLabel = new ChatComponentTranslation("tempora.command.help.label.arguments");
            commandArgumentsLabel.getChatStyle()
                .setUnderlined(true);
            commandArgumentsLabel.getChatStyle()
                .setColor(EnumChatFormatting.AQUA);

            IChatComponent commandExampleLabel = new ChatComponentTranslation("tempora.command.help.label.example");
            commandExampleLabel.getChatStyle()
                .setUnderlined(true);
            commandExampleLabel.getChatStyle()
                .setColor(EnumChatFormatting.AQUA);

            sender.addChatMessage(descriptionSeparator);
            sender.addChatMessage(commandUsageLabel);
            sender.addChatMessage(commandUsage);

            CommandUtils.sendNewLine(sender);

            sender.addChatMessage(commandDescriptionLabel);
            sender.addChatMessage(temporaCommand.getCommandDescription());

            CommandUtils.sendNewLine(sender);

            List<IChatComponent> argsDescriptions = temporaCommand.getArgsDescriptions();

            if (!argsDescriptions.isEmpty()) {
                sender.addChatMessage(commandArgumentsLabel);
                for (IChatComponent argDescription : argsDescriptions) {
                    sender.addChatMessage(argDescription);
                }

                CommandUtils.sendNewLine(sender);
            }

            sender.addChatMessage(commandExampleLabel);
            sender.addChatMessage(temporaCommand.getCommandExample());
            sender.addChatMessage(temporaCommand.getCommandExampleDescription());
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
}
