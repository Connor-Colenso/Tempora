package com.colen.tempora.commands.command_base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.commands.TemporaCommand;

/**
 * One subcommand of /tempora, e.g. "lookup" or "region set". Lang keys live under tempora.command.<path>, with the
 * path's spaces swapped for dots: .description for the help text and .arg0, .arg1, ... for each argument.
 */
public abstract class TemporaSubcommand {

    private final String name;
    private final List<String> arguments;
    private String path;

    // Each argument is how it shows in the usage line, e.g. "<name>" or "[dim_ID]".
    protected TemporaSubcommand(String name, String... arguments) {
        this.name = name;
        this.path = name;
        this.arguments = Arrays.asList(arguments);
    }

    public abstract void execute(ICommandSender sender, String[] args);

    // Example arguments for the help page, e.g. "spawn change on".
    public abstract String getExampleArgs();

    public List<String> tabComplete(ICommandSender sender, String[] args) {
        return null;
    }

    public boolean isPlayerOnly() {
        return false;
    }

    // Hidden subcommands still run, but are left out of help and tab completion. For ones only meant to be clicked
    // in chat.
    public boolean isHidden() {
        return false;
    }

    public final String getName() {
        return name;
    }

    // E.g. "region set".
    public final String getPath() {
        return path;
    }

    void setParentPath(String parentPath) {
        path = parentPath.isEmpty() ? name : parentPath + " " + name;
    }

    public final String getLangKey() {
        return "tempora.command." + path.replace(' ', '.');
    }

    public IChatComponent getDescription() {
        return new ChatComponentTranslation(getLangKey() + ".description");
    }

    // The command as typed, e.g. "/tempora region set", plus any arguments given.
    public final String command(Object... args) {
        StringBuilder command = new StringBuilder("/").append(TemporaCommand.NAME)
            .append(' ')
            .append(path);
        for (Object arg : args) {
            command.append(' ')
                .append(arg);
        }
        return command.toString();
    }

    // Built from raw formatting codes, as IChatComponent.getFormattedText() only exists on the client.
    public String getUsage() {
        StringBuilder usage = new StringBuilder(command());

        int colourIndex = 0;
        for (String argument : arguments) {
            usage.append(' ')
                .append(getColourAtIndex(colourIndex++))
                .append(argument);
        }

        return usage.append(EnumChatFormatting.RESET)
            .toString();
    }

    // The usage line, clickable to fill the command into chat, with the description on hover.
    public IChatComponent getUsageChatComponent() {
        IChatComponent usage = new ChatComponentText(getUsage());
        usage.getChatStyle()
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command() + " "))
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, getDescription()));
        return usage;
    }

    // E.g. "<name>: Name of the region." for each argument.
    public List<IChatComponent> getArgumentDescriptions() {
        List<IChatComponent> descriptions = new ArrayList<>();

        for (int i = 0; i < arguments.size(); i++) {
            IChatComponent argument = new ChatComponentText(arguments.get(i));
            argument.getChatStyle()
                .setColor(getColourAtIndex(i));

            descriptions.add(
                new ChatComponentTranslation(
                    "%s: %s",
                    argument,
                    new ChatComponentTranslation(getLangKey() + ".arg" + i)));
        }

        return descriptions;
    }

    protected final void sendUsageError(ICommandSender sender) {
        IChatComponent usage = new ChatComponentTranslation("tempora.command.usage", getUsage());
        usage.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        sender.addChatMessage(usage);

        IChatComponent hint = new ChatComponentTranslation(
            "tempora.command.usage.hint",
            "/" + TemporaCommand.NAME + " help " + path);
        hint.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        sender.addChatMessage(hint);
    }

    protected static void sendError(ICommandSender sender, String langKey, Object... args) {
        IChatComponent error = new ChatComponentTranslation(langKey, args);
        error.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        sender.addChatMessage(error);
    }

    private static final List<EnumChatFormatting> ARGS_COLOUR_LIST = Arrays.asList(
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
        EnumChatFormatting.LIGHT_PURPLE);

    // Loop the colours if you reach the end.
    private static EnumChatFormatting getColourAtIndex(int index) {
        return ARGS_COLOUR_LIST.get(index % ARGS_COLOUR_LIST.size());
    }
}
