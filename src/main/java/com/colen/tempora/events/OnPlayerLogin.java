package com.colen.tempora.events;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.Tags;
import com.colen.tempora.networking.PlayerPackets;
import com.colen.tempora.networking.packets.PacketServerVersion;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.utils.CommandUtils;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public class OnPlayerLogin {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        warnIfRegionsUnreadable(event.player);
        sendServerVersion(event.player);
    }

    // Their client compares it with its own and tells them if it's different, see ServerVersionCheck. Only players who
    // can use /tempora, everyone else's mismatch doesn't matter to them
    public static void sendServerVersion(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP playerMP) || !CommandUtils.mayUseTempora(playerMP)) return;

        PlayerPackets.sendTo(new PacketServerVersion(Tags.VERSION), playerMP);
    }

    // Region file got set aside on server start so every region is gone until someone fixes it. Otherwise only the log
    // says so, so tell each player who can use /tempora when they join
    public static void warnIfRegionsUnreadable(EntityPlayer player) {
        String fileName = TemporaRegionRegistry.getUnreadableFileName();
        if (fileName == null) return;
        if (!(player instanceof EntityPlayerMP playerMP) || !CommandUtils.mayUseTempora(playerMP)) return;

        IChatComponent warning = new ChatComponentTranslation("tempora.warning.regions_unreadable", fileName);
        warning.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        player.addChatMessage(warning);
    }
}
