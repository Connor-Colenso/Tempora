package com.colen.tempora.chat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import com.gtnewhorizon.gtnhlib.chat.AbstractChatComponentCustom;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.AbstractChatComponentNumber;

public class ChatComponentTimeAbsolute extends AbstractChatComponentNumber {

    public ChatComponentTimeAbsolute(Number epochMillis) {
        super(epochMillis);
    }

    public ChatComponentTimeAbsolute() {}

    @Override
    protected String formatNumber(Number value) {

        long pastTimestamp = value.longValue();

        Instant past = Instant.ofEpochMilli(pastTimestamp);
        ZoneId zoneId = TimeZone.getDefault()
            .toZoneId();

        ZonedDateTime localDateTime = ZonedDateTime.ofInstant(past, zoneId);

        return localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }

    @Override
    public String getID() {
        return "ChatComponentTimeAbsolute";
    }

    @Override
    protected AbstractChatComponentCustom copySelf() {
        return new ChatComponentTimeAbsolute();
    }
}
