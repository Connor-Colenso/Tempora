package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.NETWORK;

import java.sql.ResultSet;
import java.sql.SQLException;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.enums.LoggerEnum;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public abstract class GenericQueueElement implements IMessage {

    public double x;
    public double y;
    public double z;
    public int dimensionId;
    public long timestamp;
    public String eventID;

    // This field purely dictates when an event was made, so we know when to stop rendering it in world. It is only
    // relevant on the client.
    public long eventRenderCreationTime;

    public abstract IChatComponent localiseText(String uuid);

    public void populateDefaultFieldsFromResultSet(ResultSet resultSet) throws SQLException {
        x = resultSet.getDouble("x");
        y = resultSet.getDouble("y");
        z = resultSet.getDouble("z");
        dimensionId = resultSet.getInt("dimensionID");
        timestamp = resultSet.getLong("timestamp");
        eventID = resultSet.getString("eventID");
    }

    public void sendEventToClientForRendering(EntityPlayerMP player) {
        NETWORK.sendTo(this, player);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        dimensionId = buf.readInt();
        timestamp = buf.readLong();
        eventID = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(dimensionId);
        buf.writeLong(timestamp);
        ByteBufUtils.writeUTF8String(buf, eventID);
    }

    public boolean needsTransparencyToRender() {
        return false;
    }

    public abstract LoggerEnum getLoggerType();

    // How the x y z should be shown in chat and in the /tp command.
    public enum CoordFormat {

        // Whole blocks – rounds to the nearest integer.
        INT("%.0f") {

            @Override
            String command(double v) { // use same rounded value
                return Long.toString(Math.round(v));
            }
        },

        // One decimal place.
        FLOAT_1DP("%.1f");

        private final String printf; // e.g. "%.1f"

        CoordFormat(String printf) {
            this.printf = printf;
        }

        // Text shown to the player.
        String display(double v) {
            return String.format(printf, v);
        }

        // Value inserted into the /tp command (override if special).
        String command(double v) {
            return display(v);
        }
    }

    public static IChatComponent generateTeleportChatComponent(double x, double y, double z, int dimId,
        String playerName, CoordFormat fmt) {

        // Translation‑driven teleport options.
        IChatComponent display = new ChatComponentTranslation(
            "tempora.teleport.display",
            fmt.display(x),
            fmt.display(y),
            fmt.display(z));

        String cmd = "/cofh tpx " + playerName
            + " "
            + fmt.command(x)
            + " "
            + fmt.command(y)
            + " "
            + fmt.command(z)
            + " "
            + dimId;

        IChatComponent hoverText = new ChatComponentTranslation(
            "tempora.teleport.hover",
            fmt.display(x),
            fmt.display(y),
            fmt.display(z),
            dimId);
        hoverText.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        display.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        return display;
    }

    public static IChatComponent generateUndoCommand(LoggerEnum loggerName, String eventID) {

        // Translation‑driven teleport options.
        IChatComponent display = new ChatComponentTranslation("tempora.undo.query.display");

        // Todo link to command file, using a constant
        String cmd = "/tempora_undo " + loggerName + " " + eventID;

        IChatComponent hoverText = new ChatComponentTranslation("tempora.undo.query.hover");
        hoverText.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        display.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        return display;
    }
}
