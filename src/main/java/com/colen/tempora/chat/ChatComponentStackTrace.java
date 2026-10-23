package com.colen.tempora.chat;

import java.nio.charset.StandardCharsets;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import com.colen.tempora.chat.clientclick.AbstractClickableChatComponent;
import com.gtnewhorizon.gtnhlib.chat.AbstractChatComponentCustom;

/**
 * [Trace] on a block change: carries the whole stack trace that made the change, and prints it in the clicking player's
 * own chat. Nothing is asked of the server, so it works however long ago the line arrived, even after a restart.
 */
public class ChatComponentStackTrace extends AbstractClickableChatComponent<ChatComponentStackTrace> {

    private String trace = "";

    public ChatComponentStackTrace(String trace) {
        this.trace = trace;
    }

    public ChatComponentStackTrace() {}

    public String getTrace() {
        return trace;
    }

    @Override
    public String getUnformattedTextForChat() {
        return StatCollector.translateToLocal("tempora.trace.button");
    }

    // Client side.
    @Override
    protected void onClicked() {
        ClientStackTracePrinter.print(trace);
    }

    @Override
    public void encode(PacketBuffer buf) {
        byte[] bytes = trace.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    @Override
    public void decode(PacketBuffer buf) {
        byte[] bytes = new byte[buf.readInt()];
        buf.readBytes(bytes);
        trace = new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public String getID() {
        return "TemporaStackTrace";
    }

    @Override
    protected AbstractChatComponentCustom copySelf() {
        return new ChatComponentStackTrace(trace);
    }
}
