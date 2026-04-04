package com.colen.tempora.loggers.block_change;

import static com.colen.tempora.utils.CommandUtils.generateUndoCommand;
import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;

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

        // Split stack trace and reverse to show deepest first
        String[] traceLines = stackTrace.split("->");
        int maxLines = 7;
        int totalLines = traceLines.length;
        int start = Math.max(0, totalLines - maxLines); // only last maxLines
        ChatComponentText hoverText = new ChatComponentText("");

        // Iterate backwards from the deepest line
        int index = 0;
        for (int i = totalLines - 1; i >= start; i--) {
            IChatComponent line = new ChatComponentText(traceLines[i].trim());
            line.getChatStyle().setColor(EnumChatFormatting.GRAY);

            IChatComponent lineNumber = new ChatComponentText(traceLines.length - (index++) - 1 + ".");
            lineNumber.getChatStyle().setColor(EnumChatFormatting.YELLOW);

            hoverText.appendSibling(new ChatComponentTranslation("%s %s", lineNumber, line));
            if (i != 0) hoverText.appendText("\n");
        }

        // Add ellipsis if truncated
        if (totalLines > maxLines) {
            IChatComponent ellipsis =  new ChatComponentText("...");
            ellipsis.getChatStyle().setColor(EnumChatFormatting.GRAY);
            hoverText.appendSibling(ellipsis);
        }

        // Create hover component for stack trace placeholder
        ChatComponentTranslation stackTraceComponent = new ChatComponentTranslation("tempora.text.stacktrace");
        stackTraceComponent.getChatStyle().setColor(EnumChatFormatting.AQUA);
        stackTraceComponent.getChatStyle().setChatHoverEvent(
            new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)
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
