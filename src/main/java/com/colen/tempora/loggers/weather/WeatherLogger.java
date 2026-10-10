package com.colen.tempora.loggers.weather;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericGlobalLogger;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * Logs each dimension's weather changing between clear, rain and thunder, whatever changed it: the weather cycle,
 * /weather, or another mod. Dimensions without a sky, like the Nether, are skipped as they never have weather.
 */
public class WeatherLogger extends GenericGlobalLogger<WeatherEventInfo> {

    // Each world's weather when it was last checked. A world seen for the first time is only noted, as there is no
    // change to log yet. Weak, so unloaded worlds are let go, and a world loaded again starts afresh.
    private final Map<World, Weather> lastWeather = new WeakHashMap<>();

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.WEATHER;
    }

    @Override
    public @NotNull String getShortName() {
        return "weather";
    }

    @Override
    public @NotNull WeatherEventInfo newEventInfo() {
        return new WeatherEventInfo();
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.MinecraftEvent;
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (!isEnabledByConfig) return;
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER) return;
        if (event.world.provider.hasNoSky) return;

        Weather weather = Weather.of(event.world);
        Weather previous = lastWeather.put(event.world, weather);
        if (previous == null || previous == weather) return;

        logWeatherChange(event.world.provider.dimensionId, previous, weather);
    }

    /** Logs a dimension's weather changing. */
    public void logWeatherChange(int dimensionID, Weather previous, Weather weather) {
        if (!isEnabledByConfig) return;

        WeatherEventInfo eventInfo = new WeatherEventInfo();
        eventInfo.dimensionID = dimensionID;
        eventInfo.previousWeather = previous.name();
        eventInfo.weather = weather.name();

        logEvent(eventInfo);
    }
}
