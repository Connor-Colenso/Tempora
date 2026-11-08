package com.colen.tempora.loggers.block_change;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.event.HoverEvent;
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

    @Column
    public int beforeBlockID;

    @Column
    public int beforeMetadata;

    @Column
    public int beforePickBlockID;

    @Column
    public int beforePickBlockMeta;

    @Column(type = ColumnType.NBT)
    public String beforeEncodedNBT = NBTUtils.NO_NBT;

    @Column
    public int afterBlockID;

    @Column
    public int afterMetadata;

    @Column
    public int afterPickBlockID;

    @Column
    public int afterPickBlockMeta;

    @Column(type = ColumnType.NBT)
    public String afterEncodedNBT = NBTUtils.NO_NBT;

    // Trace's number in the trace table, set as the event is written (see BlockChangeLogger.writeExtras), 0 if none.
    // Not sent to the client
    @Column
    public long stackTraceID;

    // Trace hash (StackTraceUtils.getID) that writeExtras uses to find its number. Not stored
    public long stackTraceHash;

    @Column(type = ColumnType.PLAYER)
    public String closestPlayerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    // Whole blocks, a whole number takes a few bytes where any other double takes eight
    @Column
    public double closestPlayerDistance;

    private String stackTrace;

    @Override
    public IChatComponent localiseText() {
        IChatComponent when = EventChat.time(timestamp);
        IChatComponent before = EventChat.block(beforePickBlockID, beforePickBlockMeta, beforeBlockID, beforeMetadata);
        IChatComponent after = EventChat.block(afterPickBlockID, afterPickBlockMeta, afterBlockID, afterMetadata);
        IChatComponent where = EventChat.where(this, CommandUtils.TeleportType.BLOCK);
        IChatComponent trace = traceComponent();

        // Negative distance means no player was in the world
        if (closestPlayerDistance < 0) {
            return EventChat.line("tempora.event.block_change.no_player", when, before, after, where, trace);
        }

        // Only the nearest player not necessarily who did it, so how near they were goes on hover
        IChatComponent closestPlayer = EventChat.closestPlayer(closestPlayerUUID, closestPlayerDistance);

        return EventChat.line("tempora.event.block_change", when, before, after, where, closestPlayer, trace);
    }

    // [Trace] shows the end of the trace on hover and prints all of it on click. Whole trace travels with the line so
    // the click still works however old the line is
    private IChatComponent traceComponent() {
        if (stackTrace == null) {
            // Never stored or already pruned
            IChatComponent missing = new ChatComponentTranslation("tempora.trace.missing");
            missing.getChatStyle()
                .setColor(EnumChatFormatting.DARK_GRAY);
            return missing;
        }

        List<IChatComponent> stackTraceComponents = StackTraceUtils.toChatLines(stackTrace);

        // Hover text, truncated
        int maxLines = 7;
        List<IChatComponent> hoverLines = new ArrayList<>();
        int totalLines = stackTraceComponents.size();

        for (int i = 0; i < Math.min(maxLines, totalLines); i++) {
            hoverLines.add(stackTraceComponents.get(i));
        }

        if (totalLines > maxLines) {
            IChatComponent number = new ChatComponentNumber(stackTraceComponents.size());
            number.getChatStyle()
                .setColor(EnumChatFormatting.RED);

            IChatComponent stackTraceTooLongMsg = new ChatComponentTranslation("tempora.trace.hover.more", number);
            stackTraceTooLongMsg.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);
            hoverLines.add(stackTraceTooLongMsg);
        }

        // Title in the [Trace] button colour so the first trace line isn't drawn as a white title
        IChatComponent title = new ChatComponentTranslation("tempora.trace.hover.title");
        title.getChatStyle()
            .setColor(EnumChatFormatting.AQUA);
        IChatComponent hoverText = EventChat.titledHover(title, hoverLines.toArray(new IChatComponent[0]));

        IChatComponent stackTraceComponent = new ChatComponentStackTrace(stackTrace);
        stackTraceComponent.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));
        return stackTraceComponent;
    }

    // Trace that changed the block, read from the trace table along with the event
    void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    // Trace that changed the block once read back, null if it isn't stored
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
