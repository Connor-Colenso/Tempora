package com.colen.tempora.loggers.weather;

import static com.colen.tempora.utils.GenericUtils.getDimensionName;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GlobalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.TimeUtils;

public class WeatherEventInfo extends GlobalEventInfo {

    // Weather is per dimension, though most dimensions follow the overworld's.
    @Column(constraints = "NOT NULL")
    public int dimensionID;

    // Weather names: CLEAR, RAIN or THUNDER.
    @Column(constraints = "NOT NULL")
    public String weather;

    @Column(constraints = "NOT NULL")
    public String previousWeather;

    @Override
    public IChatComponent localiseText(String commandIssuerUUID) {
        return new ChatComponentTranslation(
            "message.weather",
            getDimensionName(dimensionID),
            dimensionID,
            Weather.chatComponent(previousWeather),
            Weather.chatComponent(weather),
            TimeUtils.formatTime(timestamp));
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.WEATHER;
    }
}
