package com.colen.tempora.commands;

import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.TemporaCommandBase;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.colen.tempora.utils.CommandUtils.OP_ONLY;

public class HelpCommand extends TemporaCommandBase {

    private static final Map<String, ICommand> command_map = MinecraftServer.getServer().getCommandManager().getCommands();

    @Override
    public String getCommandName() {
        return "tempora_help";
    }

    @Override
    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.help.help.description");
    }

    @Override
    public IChatComponent getCommandExample() {
        return new ChatComponentTranslation("tempora.command.help.help.example");
    }

    @Override
    public List<IChatComponent> getArgsDescriptions() {
        ArrayList<IChatComponent> argsDescriptions = new ArrayList<>();

        argsDescriptions.add(new ChatComponentTranslation("tempora.command.help.help.arg1"));

        return argsDescriptions;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "§a/tempora_help §6<command_name>§r";
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
        ICommand command = command_map.get(args[0]);

        if (command == null) {
            ChatComponentTranslation invalidCommand = new ChatComponentTranslation(
                "tempora.command.help.command_not_found", args[0]);
            sender.addChatMessage(invalidCommand);
            return;
        }

        if (command instanceof TemporaCommandBase temporaCommand) {
            IChatComponent descriptionSeparator = new ChatComponentText("-----------------------------------------------------");
            descriptionSeparator.getChatStyle().setColor(EnumChatFormatting.DARK_GRAY);

            IChatComponent commandUsageLabel = new ChatComponentTranslation("tempora.command.help.label.usage");
            commandUsageLabel.getChatStyle().setUnderlined(true);
            commandUsageLabel.getChatStyle().setColor(EnumChatFormatting.AQUA);

            IChatComponent commandUsage = new ChatComponentText(temporaCommand.getCommandUsage(sender));

            IChatComponent commandDescriptionLabel = new ChatComponentTranslation("tempora.command.help.label.description");
            commandDescriptionLabel.getChatStyle().setUnderlined(true);
            commandDescriptionLabel.getChatStyle().setColor(EnumChatFormatting.AQUA);

            IChatComponent commandArgumentsLabel = new ChatComponentTranslation("tempora.command.help.label.arguments");
            commandArgumentsLabel.getChatStyle().setUnderlined(true);
            commandArgumentsLabel.getChatStyle().setColor(EnumChatFormatting.AQUA);

            IChatComponent commandExampleLabel = new ChatComponentTranslation("tempora.command.help.label.example");
            commandExampleLabel.getChatStyle().setUnderlined(true);
            commandExampleLabel.getChatStyle().setColor(EnumChatFormatting.AQUA);

            sender.addChatMessage(descriptionSeparator);                                   // ------------------------------------
            sender.addChatMessage(commandUsageLabel);                                      // Usage
            sender.addChatMessage(commandUsage);                                           // /command <args1> <args2> ...
            CommandUtils.sendNewLine(sender);                                              //
            sender.addChatMessage(commandDescriptionLabel);                                // Description
            sender.addChatMessage(temporaCommand.getCommandDescription());                 // This command does...
            CommandUtils.sendNewLine(sender);                                              //
            sender.addChatMessage(commandArgumentsLabel);                                  // Arguments
            for (IChatComponent argDescription : temporaCommand.getArgsDescriptions()) {   // <args1>: This argument is used for...
                sender.addChatMessage(argDescription);                                     // <args2>: ...
            }                                                                              // ...
            CommandUtils.sendNewLine(sender);                                              //
            sender.addChatMessage(commandExampleLabel);                                    // Example
            sender.addChatMessage(temporaCommand.getCommandExample());                     // /command test: This would...
            sender.addChatMessage(descriptionSeparator);                                   // -------------------------------------

        } else {
            sender.addChatMessage(new ChatComponentTranslation(
                "tempora.command.help.command_not_from_tempora", args[0]));
        }
    }

    private List<String> completeTemporaCommandNames(String[] args) {
        List<String> temporaCommandNames = new java.util.ArrayList<>();

        for (ICommand command : HelpCommand.command_map.values()){
            if (command instanceof TemporaCommandBase temporaCommand){
                temporaCommandNames.add(temporaCommand.getCommandName());
            }
        }

        return CommandBase.getListOfStringsMatchingLastWord(args, temporaCommandNames.toArray(new String[0])
        );
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return completeTemporaCommandNames(args);
    }
}
