package com.colen.tempora.loggers.block_change;

import static com.colen.tempora.utils.ChatUtils.ONE_DP;
import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.commands.TraceCommand;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.StackTraceUtils;
import com.colen.tempora.utils.nbt.NBTUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class BlockChangeEventInfo extends PositionalEventInfo {

    @Column(constraints = "NOT NULL")
    public int beforeBlockID;

    @Column(constraints = "NOT NULL")
    public int beforeMetadata;

    @Column(constraints = "NOT NULL")
    public int beforePickBlockID;

    @Column(constraints = "NOT NULL")
    public int beforePickBlockMeta;

    @Column(constraints = "NOT NULL")
    public String beforeEncodedNBT = NBTUtils.NO_NBT;

    @Column(constraints = "NOT NULL")
    public int afterBlockID;

    @Column(constraints = "NOT NULL")
    public int afterMetadata;

    @Column(constraints = "NOT NULL")
    public int afterPickBlockID;

    @Column(constraints = "NOT NULL")
    public int afterPickBlockMeta;

    @Column(constraints = "NOT NULL")
    public String afterEncodedNBT = NBTUtils.NO_NBT;

    // Not bothering to send to client.
    @Column(constraints = "NOT NULL")
    public long stackTraceID;

    @Column(constraints = "NOT NULL")
    public String closestPlayerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    @Column(constraints = "NOT NULL")
    public double closestPlayerDistance;

    @Column(constraints = "NOT NULL")
    public boolean isWorldGen;

    private String stackTrace;

    @Override
    public IChatComponent localiseText() {
        IChatComponent when = EventChat.time(timestamp);
        IChatComponent before = EventChat.block(beforePickBlockID, beforePickBlockMeta, beforeBlockID, beforeMetadata);
        IChatComponent after = EventChat.block(afterPickBlockID, afterPickBlockMeta, afterBlockID, afterMetadata);
        IChatComponent where = EventChat.where(this, CommandUtils.TeleportType.BLOCK);
        IChatComponent trace = traceComponent();

        // A negative distance means no player was in the world.
        if (closestPlayerDistance < 0) {
            return EventChat.line("message.block_change.no_player", when, before, after, where, trace);
        }

        // Only the nearest player, not necessarily who did it, so how near they were is on hover.
        IChatComponent closestPlayer = EventChat.player(closestPlayerUUID);
        closestPlayer.getChatStyle()
            .setChatHoverEvent(
                new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    EventChat.grey(
                        new ChatComponentTranslation(
                            "message.block_change.closest_player",
                            formatNumber(closestPlayerDistance, ONE_DP)))));

        return EventChat.line("message.block_change", when, before, after, where, closestPlayer, trace);
    }

    // [Trace], showing the end of the stack trace that changed the block on hover, and all of it on click.
    private IChatComponent traceComponent() {
        String stackTrace = StackTraceUtils.findStackTrace(stackTraceID);
        if (stackTrace == null) {
            // Trace IDs are only kept until the server stops, so changes logged before a restart have none.
            IChatComponent missing = new ChatComponentTranslation("tempora.command.stacktrace.missing");
            missing.getChatStyle()
                .setColor(EnumChatFormatting.DARK_GRAY);
            return missing;
        }

        // Generate full stack trace as a list of IChatComponents
        List<IChatComponent> stackTraceComponents = generateStackTraceComponents(stackTrace);

        // Stored under the trace's own ID, so showing the same trace again replaces it rather than storing another
        // copy.
        String traceKey = String.valueOf(stackTraceID);
        TraceCommand.storeStackTrace(traceKey, stackTraceComponents);

        // Build hover text with truncation
        int maxLines = 7;
        ChatComponentText hoverText = new ChatComponentText("");
        int totalLines = stackTraceComponents.size();
        int start = Math.max(0, totalLines - maxLines);

        for (int i = totalLines - 1; i >= start; i--) {
            IChatComponent line = stackTraceComponents.get(i);
            hoverText.appendSibling(line);
            if (i != start) hoverText.appendText("\n");
        }

        if (totalLines > maxLines) {
            IChatComponent number = new ChatComponentNumber(stackTraceComponents.size());
            number.getChatStyle()
                .setColor(EnumChatFormatting.RED);

            IChatComponent stackTraceTooLongMsg = new ChatComponentTranslation(
                "tempora.command.click.reveal.stacktrace",
                number);
            stackTraceTooLongMsg.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);
            hoverText.appendText("\n");
            hoverText.appendSibling(stackTraceTooLongMsg);
        }

        // Stack trace hover component with click event
        ChatComponentTranslation stackTraceComponent = new ChatComponentTranslation(
            "tempora.command.stacktrace.brackets");
        stackTraceComponent.getChatStyle()
            .setColor(EnumChatFormatting.AQUA);
        stackTraceComponent.getChatStyle()
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        // Add click event to run the command
        stackTraceComponent.getChatStyle()
            .setChatClickEvent(
                new net.minecraft.event.ClickEvent(
                    net.minecraft.event.ClickEvent.Action.RUN_COMMAND,
                    TemporaCommand.TRACE.command(traceKey)));

        return stackTraceComponent;
    }

    // Generates a full stack trace as a list of IChatComponents
    private List<IChatComponent> generateStackTraceComponents(String stackTrace) {
        String[] lines = stackTrace.split("->");
        List<IChatComponent> components = new ArrayList<>();

        int index = 0;

        // Iterate in reverse (most recent first)
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();

            // Extract class, method, line
            // Format: fully.qualified.Class#method:line
            String className = trimmed;
            String methodName = "";
            String lineNumber = "?";

            int hashIndex = trimmed.lastIndexOf('#');
            int colonIndex = trimmed.lastIndexOf(':');

            if (hashIndex != -1) {
                className = trimmed.substring(0, hashIndex);
                methodName = trimmed.substring(hashIndex + 1, colonIndex != -1 ? colonIndex : trimmed.length());
            }

            if (colonIndex != -1 && colonIndex + 1 < trimmed.length()) {
                String rawLine = trimmed.substring(colonIndex + 1);
                lineNumber = rawLine.equals("-1") ? "?" : rawLine;
            }

            // Strip package → keep simple class name
            int lastDot = className.lastIndexOf('.');
            if (lastDot != -1 && lastDot + 1 < className.length()) {
                className = className.substring(lastDot + 1);
            }

            // Build simple display with 1 space
            String finalText = className + "#" + methodName + ":" + lineNumber;

            // Index component
            IChatComponent lineNumberComp = new ChatComponentTranslation("%s. ", new ChatComponentNumber(index));
            lineNumberComp.getChatStyle()
                .setColor(EnumChatFormatting.YELLOW);

            // Text component
            IChatComponent lineText = new ChatComponentText(finalText);
            lineText.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);

            IChatComponent fullLine = new ChatComponentTranslation("%s%s", lineNumberComp, lineText);
            components.add(fullLine);

            index++;
        }

        return components;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);

        beforeBlockID = buf.readInt();
        beforeMetadata = buf.readInt();
        beforePickBlockID = buf.readInt();
        beforePickBlockMeta = buf.readInt();
        beforeEncodedNBT = ByteBufUtils.readUTF8String(buf);

        afterBlockID = buf.readInt();
        afterMetadata = buf.readInt();
        afterPickBlockID = buf.readInt();
        afterPickBlockMeta = buf.readInt();
        afterEncodedNBT = ByteBufUtils.readUTF8String(buf);

        closestPlayerUUID = ByteBufUtils.readUTF8String(buf);
        closestPlayerDistance = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);

        buf.writeInt(beforeBlockID);
        buf.writeInt(beforeMetadata);
        buf.writeInt(beforePickBlockID);
        buf.writeInt(beforePickBlockMeta);
        ByteBufUtils.writeUTF8String(buf, beforeEncodedNBT);

        buf.writeInt(afterBlockID);
        buf.writeInt(afterMetadata);
        buf.writeInt(afterPickBlockID);
        buf.writeInt(afterPickBlockMeta);
        ByteBufUtils.writeUTF8String(buf, afterEncodedNBT);

        ByteBufUtils.writeUTF8String(buf, closestPlayerUUID);
        buf.writeDouble(closestPlayerDistance);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.BLOCK_CHANGE;
    }
}
