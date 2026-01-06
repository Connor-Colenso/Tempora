package com.colen.tempora.chat;

import static com.colen.tempora.utils.ChatUtils.ONE_DP;

import net.minecraft.util.StatCollector;

import com.colen.tempora.utils.TimeUtils;
import com.gtnewhorizon.gtnhlib.chat.AbstractChatComponentCustom;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.AbstractChatComponentNumber;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

public class ChatComponentTimeRelative extends AbstractChatComponentNumber {

    public ChatComponentTimeRelative(Number epochMillis) {
        super(epochMillis);
    }

    public ChatComponentTimeRelative() {}

    // Client side call.
    @Override
    protected String formatNumber(Number value) {
        long pastTimestamp = value.longValue();

        TimeUtils.DurationParts formattedTime = TimeUtils.relativeTimeAgoFormatter(pastTimestamp);

        return StatCollector.translateToLocalFormatted(
            formattedTime.translationKey,
            NumberFormatUtil.formatNumber(formattedTime.time, ONE_DP));
    }

    @Override
    public String getID() {
        return "ChatComponentTimeRelative";
    }

    @Override
    protected AbstractChatComponentCustom copySelf() {
        return new ChatComponentTimeRelative(number);
    }

}
