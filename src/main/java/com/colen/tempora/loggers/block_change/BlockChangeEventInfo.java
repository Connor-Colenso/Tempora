package com.colen.tempora.loggers.block_change;

import static com.colen.tempora.utils.CommandUtils.generateUndoCommand;
import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;

import com.colen.tempora.commands.TemporaStackTrace;
import net.minecraft.block.Block;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BlockChangeEventInfo extends GenericEventInfo {

    @Column(constraints = "NOT NULL")
    public int beforeBlockID;

    @Column(constraints = "NOT NULL")
    public int beforeMetadata;

    @Column(constraints = "NOT NULL")
    public int beforePickBlockID;

    @Column(constraints = "NOT NULL")
    public int beforePickBlockMeta;

    @Column(constraints = "NOT NULL")
    public String beforeEncodedNBT;

    @Column(constraints = "NOT NULL")
    public int afterBlockID;

    @Column(constraints = "NOT NULL")
    public int afterMetadata;

    @Column(constraints = "NOT NULL")
    public int afterPickBlockID;

    @Column(constraints = "NOT NULL")
    public int afterPickBlockMeta;

    @Column(constraints = "NOT NULL")
    public String afterEncodedNBT;

    @Column(constraints = "NOT NULL")
    public String stackTrace;

    @Column(constraints = "NOT NULL")
    public String closestPlayerUUID;

    @Column(constraints = "NOT NULL")
    public double closestPlayerDistance;

    @Column(constraints = "NOT NULL")
    public boolean isWorldGen;

    @Override
    public IChatComponent localiseText(String commandIssuerUUID) {
        // Block names
        IChatComponent beforeBlockName = BlockUtils.getUnlocalisedChatComponent(beforePickBlockID, beforePickBlockMeta);
        IChatComponent afterBlockName = BlockUtils.getUnlocalisedChatComponent(afterPickBlockID, afterPickBlockMeta);

        IChatComponent coords = teleportChatComponent(x, y, z, dimensionID);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp);

        // Closest player info
        IChatComponent closestPlayerName = PlayerUtils.playerNameFromUUID(closestPlayerUUID);
        ChatComponentNumber closestPlayerDist = new ChatComponentNumber(closestPlayerDistance);

        // Generate full stack trace as a list of IChatComponents
        List<IChatComponent> stackTraceComponents = generateStackTraceComponents(stackTrace);

        // Generate a UUID for this trace and store it
        String traceUUID = UUID.randomUUID().toString();
        TemporaStackTrace.storeStackTrace(traceUUID, stackTraceComponents);

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
            number.getChatStyle().setColor(EnumChatFormatting.RED);

            IChatComponent stackTraceTooLongMsg = new ChatComponentTranslation("tempora.command.click.reveal.stacktrace", number);
            stackTraceTooLongMsg.getChatStyle().setColor(EnumChatFormatting.GRAY);
            hoverText.appendSibling(stackTraceTooLongMsg);
        }

        // Stack trace hover component with click event
        ChatComponentTranslation stackTraceComponent = new ChatComponentTranslation("tempora.command.stacktrace.brackets");
        stackTraceComponent.getChatStyle().setColor(EnumChatFormatting.AQUA);
        stackTraceComponent.getChatStyle().setChatHoverEvent(
            new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)
        );

        // Add click event to run the command
        stackTraceComponent.getChatStyle().setChatClickEvent(
            new net.minecraft.event.ClickEvent(
                net.minecraft.event.ClickEvent.Action.RUN_COMMAND,
                "/tempora_stacktrace " + traceUUID
            )
        );

        // Main message component
        return new ChatComponentTranslation(
            "message.block_change",
            coords,
            beforeBlockName,
            beforeBlockID,
            beforeMetadata,
            afterBlockName,
            afterBlockID,
            afterMetadata,
            timeAgo,
            stackTraceComponent,
            closestPlayerName,
            closestPlayerDist,
            generateUndoCommand(getLoggerName(), eventID)
        );
    }

    // Generates a full stack trace as a list of IChatComponents
    private List<IChatComponent> generateStackTraceComponents(String stackTrace) {
        String[] lines = stackTrace.split("->");
        List<IChatComponent> components = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();

            IChatComponent lineNumber = new ChatComponentText(i + ".");
            lineNumber.getChatStyle().setColor(EnumChatFormatting.YELLOW);

            IChatComponent lineText = new ChatComponentText(trimmed);
            lineText.getChatStyle().setColor(EnumChatFormatting.GRAY);

            IChatComponent fullLine = new ChatComponentTranslation("%s %s", lineNumber, lineText);
            components.add(fullLine);
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

        stackTrace = ByteBufUtils.readUTF8String(buf);
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

        ByteBufUtils.writeUTF8String(buf, stackTrace);
        ByteBufUtils.writeUTF8String(buf, closestPlayerUUID);
        buf.writeDouble(closestPlayerDistance);
    }

    @Override
    public boolean needsTransparencyToRender() {
        return !Block.getBlockById(beforePickBlockID)
            .isOpaqueCube();
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.BLOCK_CHANGE;
    }
}
