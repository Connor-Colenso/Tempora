package com.colen.tempora.commands;

import static com.colen.tempora.utils.CommandUtils.OP_ONLY;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.commands.command_base.CommandArg;
import com.colen.tempora.commands.command_base.TemporaCommandBase;
import com.colen.tempora.utils.ChatUtils;

// Internal command for displaying stack trace info.
public class TemporaStackTrace extends TemporaCommandBase {

    private static final Map<String, List<IChatComponent>> STACK_TRACES = new HashMap<>();

    public TemporaStackTrace() {
        super(new CommandArg("key", "tempora.command.stacktrace.arg.key"));
    }

    @Override
    public String getCommandName() {
        return "tempora_stacktrace";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return OP_ONLY;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        String key = args[0];
        List<IChatComponent> trace = STACK_TRACES.get(key);

        if (trace == null || trace.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.stacktrace.not_found", key));
            return;
        }

        IChatComponent header = new ChatComponentTranslation(
            "====== %s ======",
            new ChatComponentTranslation("tempora.command.full.stacktrace"));
        header.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        sender.addChatMessage(header);

        // Print each line of the stack trace in reverse (deepest call first)
        for (int i = trace.size() - 1; i >= 0; i--) {
            sender.addChatMessage(trace.get(i));
        }

        // Hide the fact this command was run, as it is never meant to be player executed.
        ChatUtils.removeLastMessage();
    }

    @Override
    public String getExampleCommand() {
        return "todo";
    }

    @Override
    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.stacktrace.description");
    }

    @Override
    public String getTranslationKeyBase() {
        return "tempora.command.stacktrace";
    }

    public static void storeStackTrace(String key, List<IChatComponent> stackTrace) {
        STACK_TRACES.put(key, stackTrace);
    }

    public static void clearAll() {
        STACK_TRACES.clear();
    }
}
