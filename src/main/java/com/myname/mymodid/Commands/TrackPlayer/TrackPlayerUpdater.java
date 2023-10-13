package com.myname.mymodid.Commands.TrackPlayer;

import static com.myname.mymodid.Commands.TrackPlayer.PlayerTrackerUtil.queryAndSendDataToPlayer;
import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.util.HashMap;

import org.jetbrains.annotations.NotNull;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;

public class TrackPlayerUpdater {

    // Operator -> Person to be tracked.
    static final HashMap<String, String> trackerList = new HashMap<>();

    public static boolean isUserTrackingAnotherPlayer(String OPName) {
        return trackerList.containsKey(OPName);
    }

    public TrackPlayerUpdater() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    public static void addTracking(String operatorName, String playerToBeTracked) {
        trackerList.put(operatorName, playerToBeTracked);
    }

    int lastTriggered;

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onPlayerTick(final @NotNull PlayerTickEvent event) {
        // Events are only logged server side every 5 seconds at the start of a tick.
        if (event.phase != TickEvent.Phase.START) return;

        // Trigger this update every 5 seconds.
        if (event.player.ticksExisted % 100 != 0) return;

        final String OPName = event.player.getDisplayName();

        if (!isUserTrackingAnotherPlayer(OPName)) return;

        if (lastTriggered == event.player.ticksExisted) return;
        lastTriggered = event.player.ticksExisted;

        queryAndSendDataToPlayer(event.player, trackerList.get(OPName));
    }
}
