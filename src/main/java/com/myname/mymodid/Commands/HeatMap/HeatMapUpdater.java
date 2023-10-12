package com.myname.mymodid.Commands.HeatMap;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;
import org.jetbrains.annotations.NotNull;
import scala.Int;

import java.util.HashMap;

import static com.myname.mymodid.Commands.HeatMap.HeatMapUtil.queryAndSendDataToPlayer;
import static com.myname.mymodid.TemporaUtils.isClientSide;

public class HeatMapUpdater {

    // Operator -> Person to be tracked.
    static final HashMap<String, String> trackerNameList = new HashMap<>();
    // Operator -> Duration to look back for.
    static final HashMap<String, Long> trackerTimeList = new HashMap<>();

    public static boolean isUserTrackingAnotherPlayer(String OPName) {
        return trackerNameList.containsKey(OPName);
    }

    public HeatMapUpdater() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    public static void addTracking(String operatorName, long time, String playerToBeTracked) {
        trackerNameList.put(operatorName, playerToBeTracked);
        trackerTimeList.put(operatorName, time);
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onPlayerTick(final @NotNull PlayerTickEvent event) {
        // Events are only logged server side every 5 seconds at the start of a tick.
        if (isClientSide()) return;
        if (event.phase != TickEvent.Phase.END) return;

        // Trigger this update every 5 seconds.
        if (event.player.ticksExisted % 100 != 0) return;

        final String OPName = event.player.getDisplayName();

        if (!isUserTrackingAnotherPlayer(OPName)) return;

        queryAndSendDataToPlayer(event.player, trackerTimeList.get(OPName), trackerNameList.get(OPName));
    }
}
