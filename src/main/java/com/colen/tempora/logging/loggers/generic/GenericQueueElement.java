package com.colen.tempora.logging.loggers.generic;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public abstract class GenericQueueElement implements ISerializable {

    public double x;
    public double y;
    public double z;
    public int dimensionId;
    public long timestamp;

    // How the x y z should be shown in chat and in the /tp command.
    public enum CoordFormat {

        // Whole blocks – rounds to the nearest integer.
        INT("%.0f") {
            @Override
            String command(double v) {                     // use same rounded value
                return Long.toString(Math.round(v));
            }
        },

        // One decimal place.
        FLOAT_1DP("%.1f");

        private final String printf; // e.g. "%.1f"

        CoordFormat(String printf) { this.printf = printf; }

        // Text shown to the player.
        String display(double v)   { return String.format(printf, v); }

        // Value inserted into the /tp command (override if special).
        String command(double v)   { return display(v); }
    }


    protected static IChatComponent generateTeleportChatComponent(
        double x, double y, double z,
        CoordFormat fmt) {

        /* -------------------------------- display string ------------------------------- */
        String displayCoords = "[ " + fmt.display(x) + ", "
            + fmt.display(y) + ", "
            + fmt.display(z) + " ]";

        /* ------------------------------- command string --------------------------------- */
        String cmd = "/tp "
            + fmt.command(x) + " "
            + fmt.command(y) + " "
            + fmt.command(z);

        /* -------------------------- clickable chat component ---------------------------- */
        ChatComponentText coords = new ChatComponentText(displayCoords);

        coords.setChatStyle(new ChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND, cmd))
            .setChatHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ChatComponentText("§7Click to teleport"))));

        return coords;        // ready to append to any parent component
    }

}
