package com.colen.tempora.loggers.block_change;

import java.util.List;

import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.ChatComponentStackTrace;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnType;
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

    @Column(type = ColumnType.PLAYER, constraints = "NOT NULL")
    public String closestPlayerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    @Column(constraints = "NOT NULL")
    public double closestPlayerDistance;

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
            return EventChat.line("tempora.event.block_change.no_player", when, before, after, where, trace);
        }

        // Only the nearest player, not necessarily who did it, so how near they were is on hover.
        IChatComponent closestPlayer = EventChat.closestPlayer(closestPlayerUUID, closestPlayerDistance);

        return EventChat.line("tempora.event.block_change", when, before, after, where, closestPlayer, trace);
    }

    // [Trace]: the end of the stack trace that changed the block on hover, and all of it printed in chat on click. The
    // whole trace travels with the line, so the click works however long ago it arrived.
    private IChatComponent traceComponent() {
        if (stackTrace == null) {
            // Its trace was never stored or has been pruned.
            IChatComponent missing = new ChatComponentTranslation("tempora.trace.missing");
            missing.getChatStyle()
                .setColor(EnumChatFormatting.DARK_GRAY);
            return missing;
        }

        List<IChatComponent> stackTraceComponents = StackTraceUtils.toChatLines(stackTrace);

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

            IChatComponent stackTraceTooLongMsg = new ChatComponentTranslation("tempora.trace.hover.more", number);
            stackTraceTooLongMsg.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);
            hoverText.appendText("\n");
            hoverText.appendSibling(stackTraceTooLongMsg);
        }

        IChatComponent stackTraceComponent = new ChatComponentStackTrace(stackTrace);
        stackTraceComponent.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));
        return stackTraceComponent;
    }

    // The stack trace that changed the block, read from the logger's trace table along with the event.
    void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    /** The stack trace that changed the block, once read back with the event, or null if it isn't stored. */
    public @Nullable String getStackTrace() {
        return stackTrace;
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
        ByteBufUtils.writeUTF8String(buf, NBTUtils.forClient(beforeEncodedNBT, this));

        buf.writeInt(afterBlockID);
        buf.writeInt(afterMetadata);
        buf.writeInt(afterPickBlockID);
        buf.writeInt(afterPickBlockMeta);
        ByteBufUtils.writeUTF8String(buf, NBTUtils.forClient(afterEncodedNBT, this));

        ByteBufUtils.writeUTF8String(buf, closestPlayerUUID);
        buf.writeDouble(closestPlayerDistance);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.BLOCK_CHANGE;
    }
}
