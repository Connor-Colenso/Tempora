package com.colen.tempora.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public abstract class TemporaCommandBase extends CommandBase {


    public TemporaCommandBase(int... arg_groups) {
        addArgGroup(arg_groups);
        commandTranslationKeyBase = getTranslationKeyBase();
        colourFormatCommand();
    }

    private final List<Integer> argGroups = new ArrayList<>();
    private ArrayList<IChatComponent> groupedColourFormattedComponents;
    private IChatComponent formattedCommand = new ChatComponentTranslation("");
    private IChatComponent formattedExampleCommand;
    private String commandTranslationKeyBase = "";

    public final List<IChatComponent> generateCommandArgDescriptionTranslationList(String translationKeyBase) {
        ArrayList<IChatComponent> argsDescriptions = new ArrayList<>();

        int groupIndex = 0;
        // Skip the first, as it is simply the command itself e.g. /tempora_help.
        for (IChatComponent argGroup : groupedColourFormattedComponents.subList(1, groupedColourFormattedComponents.size())) {

            // %s: This describes how the grouped %s operates e.g. x1 x2 x3
            IChatComponent description = new ChatComponentTranslation("%s: ", argGroup);
            description.appendSibling(new ChatComponentTranslation(translationKeyBase + ".help.arg" + groupIndex));
            argsDescriptions.add(description);

            groupIndex++;
        }

        return argsDescriptions;
    }

    public final IChatComponent getFormattedCommand() {
        return formattedCommand;
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

    private static String getFormatStringBase(int n){
        StringBuilder formatString = new StringBuilder();
        for (int i = 0; i < n; i++) {
            formatString.append("%s ");
        }
        return formatString.toString();
    }

    private static ArrayList<IChatComponent> splitStringIntoColourFormattedArgs(String command, List<Integer> argGroups) {

        // Split into IChatComponents.
        ArrayList<IChatComponent> iChatComponents = new ArrayList<>();
        for (String s : command.split(" ")) {
            iChatComponents.add(new ChatComponentText(s));
        }

        int absoluteIndex = 0;
        int groupIndex = 0;
        ArrayList<IChatComponent> groupedColourFormattedComponents = new ArrayList<>();
        for (int groupSize : argGroups) {

            ArrayList<IChatComponent> group = new ArrayList<>();
            for (int i = 0; i < groupSize; i++) {
                group.add(iChatComponents.get(absoluteIndex + i));
            }

            IChatComponent groupComponent = new ChatComponentTranslation(getFormatStringBase(groupSize), group.toArray());
            groupComponent.getChatStyle().setColor(getColourAtIndex(groupIndex));
            groupedColourFormattedComponents.add(groupComponent);

            groupIndex++;
            absoluteIndex += groupSize;
        }

        return groupedColourFormattedComponents;
    }

    private void colourFormatCommand() {

        groupedColourFormattedComponents = splitStringIntoColourFormattedArgs(getCommandUsage(null), argGroups);

        formattedCommand = new ChatComponentText("");
        for (IChatComponent component : groupedColourFormattedComponents) {
            formattedCommand.appendSibling(component);
        }

        formattedExampleCommand = new ChatComponentText("");
        String exampleArgs = "/" + getCommandName() + " " + getExampleArgs();
        for (IChatComponent component : splitStringIntoColourFormattedArgs(exampleArgs, argGroups)) {
            formattedExampleCommand.appendSibling(component);
        }
    }

    // Loop the colours if you reach the end.
    public static EnumChatFormatting getColourAtIndex(int index) {
        return ARGS_COLOUR_LIST.get(index % ARGS_COLOUR_LIST.size());
    }

    private void addArgGroup(int... groupSize) {
        argGroups.add(1);
        for (int size : groupSize) {
            argGroups.add(size);
        }
    }

    public abstract String getExampleArgs();

    public abstract String getTranslationKeyBase();

    public final IChatComponent getCommandDescription() {
        return new ChatComponentTranslation(getTranslationKeyBase() + ".help.description");
    }

    public final IChatComponent getCommandExample() {
        return formattedExampleCommand;
    }

    public final IChatComponent getCommandExampleDescription() {
        return new ChatComponentTranslation(getTranslationKeyBase() + ".help.example_description");
    }

    public final List<IChatComponent> getArgsDescriptions() {
        return generateCommandArgDescriptionTranslationList(commandTranslationKeyBase);
    }
}
