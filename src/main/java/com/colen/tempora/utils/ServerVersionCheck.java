package com.colen.tempora.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.Tags;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Tells an admin when their Tempora isn't the same version as the server's. Clients of any version can join (see
// acceptableRemoteVersions), so after a server patches Tempora the admins' clients can be behind. The server only sends
// its version to players who can use /tempora, see PacketServerVersion
@SideOnly(Side.CLIENT)
public final class ServerVersionCheck {

    // Set on the network thread, shown on the client thread once there's a player to show it to
    private static volatile String pendingServerVersion;

    // Any thread, from PacketServerVersion
    public static void received(String serverVersion) {
        pendingServerVersion = serverVersion;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingServerVersion == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        String serverVersion = pendingServerVersion;
        pendingServerVersion = null;
        if (serverVersion.equals(Tags.VERSION)) return;

        IChatComponent warning = new ChatComponentTranslation(
            "tempora.warning.version_mismatch",
            Tags.VERSION,
            serverVersion);
        warning.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        mc.thePlayer.addChatMessage(warning);

        IChatComponent reason = new ChatComponentTranslation("tempora.warning.version_mismatch.reason");
        reason.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        mc.thePlayer.addChatMessage(reason);
    }
}
