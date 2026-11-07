package com.colen.tempora.loggers.tps;

import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericGlobalLogger;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

// Logs TPS every fixed interval of real time. Each event covers every tick since the last one, mean and slowest tick
// time and the TPS from that mean, same as /forge tps
public class TPSLogger extends GenericGlobalLogger<TPSEventInfo> {

    private static final double NANOS_PER_MILLI = 1_000_000;

    private int loggingIntervalSeconds;

    // Interval being counted, server thread only
    private long intervalStartMillis = -1;
    private int lastTickCounter;
    private int ticks;
    private long totalTickNanos;
    private long maxTickNanos;

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.TPS;
    }

    @Override
    public @NotNull String getShortName() {
        return "tps";
    }

    @Override
    public @NotNull TPSEventInfo newEventInfo() {
        return new TPSEventInfo();
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.MinecraftEvent;
    }

    @Override
    protected void addSettingsInfo(List<IChatComponent> lines) {
        super.addSettingsInfo(lines);
        lines.add(settingLine("tempora.logger.setting.interval", duration(loggingIntervalSeconds)));
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        loggingIntervalSeconds = config.getInt(
            "loggingIntervalSeconds",
            getLoggerName(),
            60,
            1,
            86_400,
            "How often the server's TPS is recorded, in seconds of real time. Each record covers every tick since the last one.");
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (!isEnabledByConfig) return;
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = MinecraftServer.getServer();
        int tickCounter = server.getTickCounter();
        long now = System.currentTimeMillis();

        // Tick counter starts at 0 per server, so a singleplayer world opened after another starts fresh instead of
        // mixing in the old world's ticks
        if (intervalStartMillis < 0 || tickCounter < lastTickCounter) {
            startInterval(now);
        }
        lastTickCounter = tickCounter;

        // How long this tick took, the server records it just before this fires
        long tickNanos = server.tickTimeArray[tickCounter % server.tickTimeArray.length];
        ticks++;
        totalTickNanos += tickNanos;
        maxTickNanos = Math.max(maxTickNanos, tickNanos);

        if (now - intervalStartMillis < loggingIntervalSeconds * 1000L) return;

        logTickTimes(ticks, totalTickNanos, maxTickNanos);
        startInterval(now);
    }

    private void startInterval(long now) {
        intervalStartMillis = now;
        ticks = 0;
        totalTickNanos = 0;
        maxTickNanos = 0;
    }

    // Logs how long ticks ticks took, total and slowest
    public void logTickTimes(int ticks, long totalTickNanos, long maxTickNanos) {
        if (!isEnabledByConfig || ticks <= 0) return;

        TPSEventInfo eventInfo = new TPSEventInfo();
        eventInfo.meanTickMs = totalTickNanos / NANOS_PER_MILLI / ticks;
        eventInfo.maxTickMs = maxTickNanos / NANOS_PER_MILLI;
        eventInfo.tps = Math.min(20, 1000 / eventInfo.meanTickMs);

        logEvent(eventInfo);
    }
}
