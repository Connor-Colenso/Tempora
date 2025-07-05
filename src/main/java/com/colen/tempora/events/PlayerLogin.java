package com.colen.tempora.events;

import static com.colen.tempora.Tempora.NETWORK;

import java.util.TimeZone;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.colen.tempora.networking.PacketTimeZone;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerLogin {

    @SubscribeEvent
    public void onClientPlayerJoin(EntityJoinWorldEvent event) {
        if (event.entity instanceof EntityPlayerSP && event.world.isRemote) {
            String timezone = TimeZone.getDefault()
                .getID();
            NETWORK.sendToServer(new PacketTimeZone(timezone));
        }
    }

}
