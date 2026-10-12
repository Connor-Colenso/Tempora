package com.colen.tempora.commands;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.commands.command_base.TemporaSubcommand;

/**
 * /tempora trace <key>
 * Prints a stack trace stored when a block change event was shown. Hidden, as it is run by clicking [Trace] in
 * chat.
 */
public class TraceCommand extends TemporaSubcommand {

    private static final Map<String, List<IChatComponent>> STACK_TRACES = new HashMap<>();

    public TraceCommand() {
        super("trace", "<key>");
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        if (args.length != 1) {
            sendUsageError(sender);
            return;
        }

        String key = args[0];
        List<IChatComponent> trace = STACK_TRACES.get(key);

        if (trace == null || trace.isEmpty()) {
            sendError(sender, "tempora.command.trace.not_found", key);
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
    }

    @Override
    public String getExampleArgs() {
        return "<key>";
    }

    public static void storeStackTrace(String key, List<IChatComponent> stackTrace) {
        STACK_TRACES.put(key, stackTrace);
    }

    public static void clearAll() {
        STACK_TRACES.clear();
    }
}
