package com.colen.tempora.chat;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import java.text.DecimalFormatSymbols;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;

import com.colen.tempora.Tempora;
import com.colen.tempora.chat.clientclick.AbstractClickableChatComponent;
import com.colen.tempora.networking.packets.PacketTeleportRequest;
import com.gtnewhorizon.gtnhlib.chat.AbstractChatComponentCustom;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatConfig;

// Clickable coords, carries a position and asks the server to teleport the clicking player there. Server decides if
// they're allowed, so a click from someone who can't use /tempora just tells them so
public class ChatComponentTeleport extends AbstractClickableChatComponent<ChatComponentTeleport> {

    private double x;
    private double y;
    private double z;
    private int dimID;

    public ChatComponentTeleport(double x, double y, double z, int dimID) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimID = dimID;
    }

    public ChatComponentTeleport() {}

    // Shown as the block it's in like F3, that's all a lookup needs and it keeps lines short. Formatted here on the
    // client so it's in the player's own number format
    @Override
    public String getUnformattedTextForChat() {
        String separator = coordinateSeparator();
        String position = formatNumber(MathHelper.floor_double(x)) + separator
            + formatNumber(MathHelper.floor_double(y))
            + separator
            + formatNumber(MathHelper.floor_double(z));
        return StatCollector.translateToLocalFormatted("tempora.teleport.display", position);
    }

    // Space between coords, unless the player's number format groups digits with spaces (1 000) where "1 000 64 1 000"
    // would be unreadable. Whole numbers so grouping is the only thing that can clash. Checked against every JDK locale
    // on Java 8, 17 and 25, the rest group with commas, full stops, apostrophes or Arabic separators
    private static String coordinateSeparator() {
        // With formatting off GTNHLib prints raw numbers, no grouping at all
        if (NumberFormatConfig.disableFormattedNotation) return " ";
        char grouping = DecimalFormatSymbols.getInstance(NumberFormatConfig.getActiveLocale())
            .getGroupingSeparator();
        // isSpaceChar not isWhitespace, the latter skips the no break spaces French and others group with
        return Character.isSpaceChar(grouping) ? ", " : " ";
    }

    // Client side
    @Override
    protected void onClicked() {
        Tempora.NETWORK.sendToServer(new PacketTeleportRequest(x, y, z, dimID));
    }

    @Override
    public void encode(PacketBuffer buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(dimID);
    }

    @Override
    public void decode(PacketBuffer buf) {
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        dimID = buf.readInt();
    }

    @Override
    public String getID() {
        return "tempora:ChatComponentTeleport";
    }

    @Override
    protected AbstractChatComponentCustom copySelf() {
        return new ChatComponentTeleport(x, y, z, dimID);
    }
}
