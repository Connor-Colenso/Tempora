package com.myname.mymodid.Commands.HeatMap;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.myname.mymodid.TemporaUtils.isClientSide;

public class HeatMapUpdater {

    // Operator -> Person to be tracked.
    static final ConcurrentHashMap<String, String> trackerNameList = new ConcurrentHashMap<>();
    // Operator -> Duration to look back for.
    static final ConcurrentHashMap<String, Long> trackerTimeList = new ConcurrentHashMap<>();
    // Operator -> Future of the ongoing task
    private final ConcurrentHashMap<String, Future<?>> ongoingTasks = new ConcurrentHashMap<>();
    // Operator -> ExecutorService for the operator's tasks
    private final ConcurrentHashMap<String, ExecutorService> executorServices = new ConcurrentHashMap<>();

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

        if (!isUserTrackingAnotherPlayer(OPName)) {
            // If the operator is not tracking, shut down their executor service if they have one
            ExecutorService executor = executorServices.remove(OPName);
            if (executor != null) {
                executor.shutdown();
            }
            return;
        }

        // Ensure the operator has an executor service
        executorServices.putIfAbsent(OPName, Executors.newSingleThreadExecutor());

        // Check if the ongoing task for this operator is done or if there's no task yet.
        Future<?> operatorTask = ongoingTasks.get(OPName);
        if (operatorTask == null || operatorTask.isDone()) {
            // Submit the new task and update the ongoingTask future for this operator.
            operatorTask = executorServices.get(OPName).submit(() -> HeatMapUtil.queryAndSendDataToPlayer(event.player, trackerTimeList.get(OPName), trackerNameList.get(OPName)));
            ongoingTasks.put(OPName, operatorTask);
        }
    }
}
