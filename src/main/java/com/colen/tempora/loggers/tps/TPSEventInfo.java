package com.colen.tempora.loggers.tps;

import static com.colen.tempora.utils.ChatUtils.ONE_DP;
import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.GlobalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;

/** The server's tick rate over one logging interval, ending at the event's timestamp. */
public class TPSEventInfo extends GlobalEventInfo {

    // Worked out as /forge tps does, from the mean tick time, so at most 20.
    @Column(constraints = "NOT NULL")
    public double tps;

    @Column(constraints = "NOT NULL")
    public double meanTickMs;

    // The slowest single tick, to show lag spikes the mean smooths over.
    @Column(constraints = "NOT NULL")
    public double maxTickMs;

    @Override
    public IChatComponent localiseText() {
        IChatComponent tpsText = new ChatComponentText(formatNumber(tps, ONE_DP));
        tpsText.getChatStyle()
            .setColor(
                tps >= 18 ? EnumChatFormatting.GREEN : tps >= 15 ? EnumChatFormatting.YELLOW : EnumChatFormatting.RED);

        return EventChat.line(
            "tempora.event.tps",
            EventChat.time(timestamp),
            tpsText,
            EventChat.text(formatNumber(meanTickMs, ONE_DP), EnumChatFormatting.WHITE),
            EventChat.text(formatNumber(maxTickMs, ONE_DP), EnumChatFormatting.WHITE));
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.TPS;
    }
}
