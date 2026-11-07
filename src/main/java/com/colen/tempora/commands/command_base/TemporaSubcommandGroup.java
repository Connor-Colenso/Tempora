package com.colen.tempora.commands.command_base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.utils.CommandUtils;

// Subcommand that only holds other subcommands, e.g. "region" holds "region create", "region list" etc. On its own it
// lists them
public class TemporaSubcommandGroup extends TemporaSubcommand {

    private final Map<String, TemporaSubcommand> children = new LinkedHashMap<>();

    public TemporaSubcommandGroup(String name, TemporaSubcommand... children) {
        super(name);
        for (TemporaSubcommand child : children) {
            add(child);
        }
    }

    public final void add(TemporaSubcommand child) {
        child.setParentPath(getPath());
        children.put(
            child.getName()
                .toLowerCase(Locale.ROOT),
            child);
    }

    @Override
    void setParentPath(String parentPath) {
        super.setParentPath(parentPath);
        for (TemporaSubcommand child : children.values()) {
            child.setParentPath(getPath());
        }
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        if (args.length == 0 || isHelpRequest(args)) {
            sendCommandList(sender);
            return;
        }

        TemporaSubcommand child = children.get(args[0].toLowerCase(Locale.ROOT));
        String[] childArgs = Arrays.copyOfRange(args, 1, args.length);

        // "/tempora lookup ?" shows the help page, console too
        if (child != null && !(child instanceof TemporaSubcommandGroup)
            && !child.isHidden()
            && isHelpRequest(childArgs)) {
            child.sendHelp(sender);
            return;
        }

        if (child == null) {
            IChatComponent unknown = new ChatComponentTranslation("tempora.command.unknown", args[0]);
            unknown.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(unknown);
            sendCommandList(sender);
            return;
        }

        if (child.isPlayerOnly() && !(sender instanceof EntityPlayerMP)) {
            sender.addChatMessage(CommandUtils.playerOnly());
            return;
        }

        child.execute(sender, childArgs);
    }

    private static boolean isHelpRequest(String[] args) {
        return args.length == 1 && args[0].equals("?");
    }

    @Override
    public List<String> tabComplete(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandBase.getListOfStringsFromIterableMatchingLastWord(args, getVisibleChildNames());
        }

        TemporaSubcommand child = children.get(args[0].toLowerCase(Locale.ROOT));
        if (child == null || child.isHidden()) return null;

        return child.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    // Follows names down through nested groups, e.g. ["region", "set"]. Null if there's no such subcommand
    public @Nullable TemporaSubcommand find(String[] names) {
        TemporaSubcommand found = this;
        for (String name : names) {
            if (!(found instanceof TemporaSubcommandGroup group)) return null;
            found = group.children.get(name.toLowerCase(Locale.ROOT));
            if (found == null) return null;
        }
        return found;
    }

    public List<String> getVisibleChildNames() {
        List<String> names = new ArrayList<>();
        for (TemporaSubcommand child : children.values()) {
            if (!child.isHidden()) names.add(child.getName());
        }
        return names;
    }

    // Every runnable non hidden subcommand under this group, nested groups included
    public List<TemporaSubcommand> getVisibleCommands() {
        List<TemporaSubcommand> commands = new ArrayList<>();
        for (TemporaSubcommand child : children.values()) {
            if (child.isHidden()) continue;

            if (child instanceof TemporaSubcommandGroup group) {
                commands.addAll(group.getVisibleCommands());
            } else {
                commands.add(child);
            }
        }
        return commands;
    }

    // One clickable usage line per subcommand, hover for what it does
    public void sendCommandList(ICommandSender sender) {
        IChatComponent header = new ChatComponentTranslation("tempora.command.list.header");
        header.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        sender.addChatMessage(header);

        for (TemporaSubcommand command : getVisibleCommands()) {
            sender.addChatMessage(command.getUsageChatComponent());
        }
    }

    @Override
    public String getExampleArgs() {
        return "";
    }
}
