package com.colen.tempora.chat;

import net.minecraft.util.StatCollector;

import com.colen.tempora.utils.TimeUtils;
import com.gtnewhorizon.gtnhlib.chat.AbstractChatComponentCustom;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.AbstractChatComponentNumber;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.gtnhlib.util.numberformatting.options.FormatOptions;

public class ChatComponentTimeRelative extends AbstractChatComponentNumber {

    public ChatComponentTimeRelative(Number epochMillis) {
        super(epochMillis);
    }

    public ChatComponentTimeRelative() {}

    private static final FormatOptions ONE_DP = new FormatOptions().setDecimalPlaces(1);

    // Client side call.
    @Override
    protected String formatNumber(Number value) {
        long pastTimestamp = value.longValue();

        TimeUtils.DurationParts formattedTime = TimeUtils.relativeTimeAgoFormatter(pastTimestamp);

        return StatCollector.translateToLocal(formattedTime.translationKey) + " "
            + NumberFormatUtil.formatNumber(formattedTime.time, ONE_DP);
    }

    @Override
    public String getID() {
        return "ChatComponentTimeRelative";
    }

    @Override
    protected AbstractChatComponentCustom copySelf() {
        return new ChatComponentTimeRelative();
    }

}
