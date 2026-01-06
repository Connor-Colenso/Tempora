package com.colen.tempora.chat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.gtnewhorizon.gtnhlib.chat.AbstractChatComponentCustom;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.AbstractChatComponentNumber;

public class ChatComponentTimeAbsolute extends AbstractChatComponentNumber {

    public ChatComponentTimeAbsolute(Number epochMillis) {
        super(epochMillis);
    }

    public ChatComponentTimeAbsolute() {}

    @Override
    protected String formatNumber(Number value) {
        return Instant.ofEpochMilli(value.longValue())
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }

    @Override
    public String getID() {
        return "ChatComponentTimeAbsolute";
    }

    @Override
    protected AbstractChatComponentCustom copySelf() {
        return new ChatComponentTimeAbsolute(number);
    }
}
