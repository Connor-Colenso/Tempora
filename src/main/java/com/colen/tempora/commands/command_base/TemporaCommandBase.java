package com.colen.tempora.commands.command_base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public abstract class TemporaCommandBase extends CommandBase {

    private final List<CommandArg> arguments = new ArrayList<>();

    public TemporaCommandBase(CommandArg... argGroups) {
        arguments.addAll(Arrays.asList(argGroups));
    }

    // todo could just shove this as variadic in the args then form translation there? Not sure.
    // This may be overridden, if they want custom information to be inserted into the ChatComponentTranslation
    public List<IChatComponent> generateCommandArgsWithDescriptions() {
        List<IChatComponent> descriptionTranslationList = new ArrayList<>();

        int colourIndex = 0;
        for (CommandArg arg : arguments) {

            IChatComponent argNames = new ChatComponentText(arg.argNames);
            argNames.getChatStyle()
                .setColor(getColourAtIndex(colourIndex++));
            IChatComponent description = new ChatComponentTranslation(arg.descriptionLangKey);

            IChatComponent layout = new ChatComponentTranslation("%s: %s", argNames, description);

            descriptionTranslationList.add(layout);
        }

        return descriptionTranslationList;
    }

    protected final static List<EnumChatFormatting> ARGS_COLOUR_LIST = new ArrayList<>(
        Arrays.asList(
            EnumChatFormatting.DARK_AQUA,
            EnumChatFormatting.GOLD,
            EnumChatFormatting.RED,
            EnumChatFormatting.YELLOW,
            EnumChatFormatting.GREEN,
            EnumChatFormatting.BLUE,
            EnumChatFormatting.DARK_PURPLE,
            EnumChatFormatting.DARK_GREEN,
            EnumChatFormatting.DARK_BLUE,
            EnumChatFormatting.DARK_RED,
            EnumChatFormatting.AQUA,
            EnumChatFormatting.LIGHT_PURPLE));

    // Loop the colours if you reach the end.
    public static EnumChatFormatting getColourAtIndex(int index) {
        return ARGS_COLOUR_LIST.get(index % ARGS_COLOUR_LIST.size());
    }

    public abstract String getExampleArgs();

    public abstract String getTranslationKeyBase();

    public abstract IChatComponent getCommandDescription();

    public final String getCommandUsage(ICommandSender ignored) {
        IChatComponent formattedExampleCommand = new ChatComponentText("/" + getCommandName() + " ");

        int colourIndex = 0;
        for (CommandArg arg : arguments) {
            IChatComponent argComponent = new ChatComponentText(arg.argNames + " ");
            argComponent.getChatStyle()
                .setColor(getColourAtIndex(colourIndex++));
            formattedExampleCommand.appendSibling(argComponent);
        }

        return formattedExampleCommand.getFormattedText();
    }

}
