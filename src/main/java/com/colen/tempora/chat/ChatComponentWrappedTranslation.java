package com.colen.tempora.chat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import com.gtnewhorizon.gtnhlib.chat.AbstractChatComponentCustom;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.AbstractChatComponentBuffer;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * A translation for a hover, translated on the client and wrapped there to three quarters of the screen's width, as
 * hover text doesn't wrap by itself: a long description would otherwise run off the screen. Each argument is a string,
 * or another of these, also translated on the client, e.g. a duration inside "Keeps events for %s".
 */
public class ChatComponentWrappedTranslation extends AbstractChatComponentBuffer<ChatComponentWrappedTranslation> {

    private static final byte STRING_ARG = 0;
    private static final byte TRANSLATION_ARG = 1;

    private String key;
    // Each a String or a ChatComponentWrappedTranslation.
    private List<Object> args;

    public ChatComponentWrappedTranslation(String key, Object... args) {
        for (Object arg : args) {
            if (!(arg instanceof String) && !(arg instanceof ChatComponentWrappedTranslation)) {
                throw new IllegalArgumentException("Arguments must be strings or wrapped translations, got " + arg);
            }
        }
        this.key = key;
        this.args = Arrays.asList(args);
    }

    public ChatComponentWrappedTranslation() {}

    public String getKey() {
        return key;
    }

    public List<Object> getArgs() {
        return args;
    }

    @Override
    public String getUnformattedTextForChat() {
        String text = translate();
        // Also called on a dedicated server, e.g. for the console, which has no font to measure with.
        if (!FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) return text;

        // A hover is drawn a line at a time, and this component's colour is only written before its first line, so
        // it is written again at the start of each line the wrapping adds. Otherwise those lines come out white.
        return ClientTextWrap.wrapToHoverWidth(text)
            .replace("\n", "\n" + getChatStyle().getFormattingCode());
    }

    // Translated, with any translated arguments filled in, but not wrapped.
    private String translate() {
        Object[] filled = new Object[args.size()];
        for (int i = 0; i < filled.length; i++) {
            Object arg = args.get(i);
            filled[i] = arg instanceof ChatComponentWrappedTranslation nested ? nested.translate() : arg;
        }
        return StatCollector.translateToLocalFormatted(key, filled);
    }

    @Override
    public void encode(PacketBuffer buf) {
        writeString(buf, key);
        buf.writeInt(args.size());
        for (Object arg : args) {
            if (arg instanceof ChatComponentWrappedTranslation nested) {
                buf.writeByte(TRANSLATION_ARG);
                nested.encode(buf);
            } else {
                buf.writeByte(STRING_ARG);
                writeString(buf, (String) arg);
            }
        }
    }

    @Override
    public void decode(PacketBuffer buf) {
        key = readString(buf);
        int count = buf.readInt();
        args = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (buf.readByte() == TRANSLATION_ARG) {
                ChatComponentWrappedTranslation nested = new ChatComponentWrappedTranslation();
                nested.decode(buf);
                args.add(nested);
            } else {
                args.add(readString(buf));
            }
        }
    }

    private static void writeString(PacketBuffer buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(PacketBuffer buf) {
        byte[] bytes = new byte[buf.readInt()];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public String getID() {
        return "ChatComponentWrappedTranslation";
    }

    @Override
    protected AbstractChatComponentCustom copySelf() {
        return new ChatComponentWrappedTranslation(key, args.toArray());
    }
}
