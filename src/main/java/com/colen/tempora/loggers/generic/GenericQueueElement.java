package com.colen.tempora.loggers.generic;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.colen.tempora.loggers.generic.column.Column;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.commands.TemporaUndoCommand;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public abstract class GenericQueueElement implements IMessage {

    @Column(constraints = "PRIMARY KEY")
    public String eventID;

    @Column(constraints = "NOT NULL")
    public double x;

    @Column(constraints = "NOT NULL")
    public double y;

    @Column(constraints = "NOT NULL")
    public double z;

    @Column(constraints = "NOT NULL")
    public int dimensionID;

    @Column(constraints = "NOT NULL")
    public long timestamp;

    @Column(constraints = "NOT NULL")
    public int versionID;

    // This field purely dictates when an event was made when received by the client, so we know when to stop rendering
    // it in world. It is only relevant on the client.
    public long eventRenderCreationTime;

    public abstract IChatComponent localiseText(String uuid);

    public void populateDefaultFieldsFromResultSet(ResultSet resultSet) throws SQLException {
        x = resultSet.getDouble("x");
        y = resultSet.getDouble("y");
        z = resultSet.getDouble("z");
        dimensionID = resultSet.getInt("dimensionID");
        timestamp = resultSet.getLong("timestamp");
        eventID = resultSet.getString("eventID");
        versionID = resultSet.getInt("versionID");
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        dimensionID = buf.readInt();
        timestamp = buf.readLong();
        eventID = ByteBufUtils.readUTF8String(buf);
        // versionID Not applicable for client.
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(dimensionID);
        buf.writeLong(timestamp);
        ByteBufUtils.writeUTF8String(buf, eventID);
        // versionID Not applicable for client.
    }

    public boolean needsTransparencyToRender() {
        return false;
    }

    public abstract String getLoggerName();

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
            new ChatComponentNumber(x),
            new ChatComponentNumber(y),
            new ChatComponentNumber(z));

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
            new ChatComponentNumber(x),
            new ChatComponentNumber(y),
            new ChatComponentNumber(z),
            dimId);
        hoverText.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        display.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        return display;
    }

    public static IChatComponent generateUndoCommand(String loggerName, String eventID) {

        // Translation‑driven teleport options.
        IChatComponent display = new ChatComponentTranslation("tempora.undo.query.display");

        String cmd = "/" + new TemporaUndoCommand().getCommandName() + " " + loggerName + " " + eventID;

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
