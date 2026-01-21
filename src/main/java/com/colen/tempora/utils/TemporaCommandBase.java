package com.colen.tempora.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public abstract class TemporaCommandBase extends CommandBase {

    public TemporaCommandBase(int... arg_groups) {
        addArgGroup(arg_groups);
        colourFormatCommand(getCommandUsage(null));
        commandTranslationKeyBase = setCommandLangBase();
    }

    public List<Integer> getArgGroups() {
        return argGroups;
    }

    private final List<Integer> argGroups = new ArrayList<>();
    private final List<ChatComponentTranslation> formattedArgs = new ArrayList<>();
    private final List<ChatComponentTranslation> formattedExampleArgs = new ArrayList<>();
    private ChatComponentTranslation formattedCommand = new ChatComponentTranslation("");
    private ChatComponentTranslation formattedExampleCommand = new ChatComponentTranslation("");
    private String commandTranslationKeyBase = "";

    public final List<IChatComponent> generateCommandArgDescriptionTranslationList(String translationKeyBase) {
        ArrayList<IChatComponent> argsDescriptions = new ArrayList<>();

        for (int i = 0; i < formattedArgs.size(); i++) {
            String translationKey = translationKeyBase + ".help.arg" + (i + 1);
            argsDescriptions.add(new ChatComponentTranslation(translationKey, formattedArgs.get(i)));
        }

        return argsDescriptions;
    }

    public final ChatComponentTranslation getFormattedCommand() {
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
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.GRAY,
            EnumChatFormatting.WHITE,
            EnumChatFormatting.AQUA,
            EnumChatFormatting.LIGHT_PURPLE));

    private void colourFormatCommand(String command) {
        int colourListIndex = 0;
        StringBuilder formattedCommandString = new StringBuilder();
        StringBuilder formattedExampleCommandString = new StringBuilder();

        formattedExampleCommandString.append("%s ");

        if (getArgGroups().isEmpty() || true) {
            for (String section : command.split(" ")) {
                formattedCommandString.append("%s ");
                ChatComponentTranslation placeholderArg = new ChatComponentTranslation(section);
                placeholderArg.getChatStyle()
                    .setColor(getColourAtIndex(colourListIndex));
                formattedArgs.add(placeholderArg);
                if (colourListIndex == 0) {
                    formattedExampleArgs.add(placeholderArg);
                }
                colourListIndex++;
            }

            String com = formattedCommandString.toString();
            formattedCommand = new ChatComponentTranslation(com, formattedArgs.toArray());
            formattedArgs.remove(0);
            colourListIndex = 1;

            for (String section : getExampleArgs().split(" ")) {
                formattedExampleCommandString.append("%s ");
                ChatComponentTranslation exampleArg = new ChatComponentTranslation(section);
                exampleArg.getChatStyle()
                    .setColor(getColourAtIndex(colourListIndex));
                formattedExampleArgs.add(exampleArg);
                colourListIndex++;
            }

            String exampleCom = formattedExampleCommandString.toString();
            formattedExampleCommand = new ChatComponentTranslation(exampleCom, formattedExampleArgs.toArray());
        } else {

        }
    }

    public final EnumChatFormatting getColourAtIndex(int index) {
        return ARGS_COLOUR_LIST.get(index % ARGS_COLOUR_LIST.size());
    }

    private void addArgGroup(int... groupSize) {
        for (int size : groupSize) {
            argGroups.add(size);
        }
    }

    public abstract String getExampleArgs();

    public abstract String setCommandLangBase();

    public final IChatComponent getCommandDescription() {
        return new ChatComponentTranslation(setCommandLangBase() + ".help.description");
    }

    public final IChatComponent getCommandExample() {
        return formattedExampleCommand;
    }

    public final IChatComponent getCommandExampleDescription() {
        return new ChatComponentTranslation(setCommandLangBase() + ".help.example_description");
    }

    public final List<IChatComponent> getArgsDescriptions() {
        return generateCommandArgDescriptionTranslationList(commandTranslationKeyBase);
    }
}
