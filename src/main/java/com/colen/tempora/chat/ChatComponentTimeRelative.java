package com.colen.tempora.chat;

import java.time.Duration;
import java.time.Instant;

import net.minecraft.util.StatCollector;

import com.gtnewhorizon.gtnhlib.chat.AbstractChatComponentCustom;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.AbstractChatComponentNumber;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.gtnhlib.util.numberformatting.options.FormatOptions;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;

public class ChatComponentTimeRelative extends AbstractChatComponentNumber {

    public ChatComponentTimeRelative(Number epochMillis) {
        super(epochMillis);
    }

    public ChatComponentTimeRelative() {}

    @Override
    protected String formatNumber(Number value) {

        long pastTimestamp = value.longValue();

        Pair<String, String> timePair = getRelativeTimeKeyAndValue(pastTimestamp);

        return StatCollector.translateToLocal(timePair.first()) + " " + timePair.second();
    }

    @Override
    public String getID() {
        return "ChatComponentTimeRelative";
    }

    @Override
    protected AbstractChatComponentCustom copySelf() {
        return new ChatComponentTimeRelative();
    }

    private static Pair<String, String> getRelativeTimeKeyAndValue(long pastTimestamp) {
        Instant past = Instant.ofEpochMilli(pastTimestamp);
        Duration duration = Duration.between(past, Instant.now());

        final double seconds = duration.toMillis() / 1000.0;
        final double minutes = seconds / 60.0;
        final double hours = minutes / 60.0;
        final double days = hours / 24.0;
        final double years = days / 365.0;
        final double decades = years / 10.0;

        String formattedValue;
        String key;

        FormatOptions formatOptions = new FormatOptions().setDecimalPlaces(1);

        if (decades >= 1) {
            key = "time.ago.decades";
            formattedValue = NumberFormatUtil.formatNumber(decades, formatOptions);
        } else if (years >= 1) {
            key = "time.ago.years";
            formattedValue = NumberFormatUtil.formatNumber(years, formatOptions);
        } else if (days >= 1) {
            key = "time.ago.days";
            formattedValue = NumberFormatUtil.formatNumber(days, formatOptions);
        } else if (hours >= 1) {
            key = "time.ago.hours";
            formattedValue = NumberFormatUtil.formatNumber(hours, formatOptions);
        } else if (minutes >= 1) {
            key = "time.ago.minutes";
            formattedValue = NumberFormatUtil.formatNumber(minutes, formatOptions);
        } else {
            key = "time.ago.seconds";
            formattedValue = NumberFormatUtil.formatNumber(seconds, formatOptions);
        }

        return new ObjectObjectImmutablePair<>(key, formattedValue);
    }
}
