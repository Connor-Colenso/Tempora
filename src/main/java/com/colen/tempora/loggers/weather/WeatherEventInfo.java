package com.colen.tempora.loggers.weather;

import static com.colen.tempora.utils.GenericUtils.getDimensionName;

import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.GlobalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

public class WeatherEventInfo extends GlobalEventInfo {

    // Weather is per dimension, though most dimensions follow the overworld's.
    @Column
    public int dimensionID;

    // Weather names: CLEAR, RAIN or THUNDER.
    @Column
    public String weather = "";

    @Column
    public String previousWeather = "";

    @Override
    public IChatComponent localiseText() {
        IChatComponent dimension = EventChat.text(getDimensionName(dimensionID), EnumChatFormatting.WHITE);
        dimension.getChatStyle()
            .setChatHoverEvent(
                new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    EventChat.grey(
                        new ChatComponentTranslation(
                            "tempora.chat.dimension_id",
                            new ChatComponentNumber(dimensionID)))));

        return EventChat.line(
            "tempora.event.weather",
            EventChat.time(timestamp),
            dimension,
            Weather.chatComponent(previousWeather),
            Weather.chatComponent(weather));
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.WEATHER;
    }
}
