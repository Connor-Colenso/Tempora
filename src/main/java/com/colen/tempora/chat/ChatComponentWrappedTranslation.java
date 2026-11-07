package com.colen.tempora.chat;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import com.gtnewhorizon.gtnhlib.chat.AbstractChatComponentCustom;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.AbstractChatComponentBuffer;

import cpw.mods.fml.common.FMLCommonHandler;

// Translation for a hover, translated and wrapped on the client to 3/4 of the screen width since hover text doesn't
// wrap by itself and a long description runs off screen. Each arg is a string, a number (formatted on the client with
// formatNumber) or another one of these also translated on the client, e.g. a duration inside "Keeps events for %s"
public class ChatComponentWrappedTranslation extends AbstractChatComponentBuffer<ChatComponentWrappedTranslation> {

    private static final byte STRING_ARG = 0;
    private static final byte TRANSLATION_ARG = 1;
    private static final byte LONG_ARG = 2;
    private static final byte DOUBLE_ARG = 3;

    private String key;
    // Each a String, Long, Double or ChatComponentWrappedTranslation
    private List<Object> args;

    public ChatComponentWrappedTranslation(String key, Object... args) {
        List<Object> checked = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg instanceof Double || arg instanceof Float) {
                checked.add(((Number) arg).doubleValue());
            } else if (arg instanceof Number number) {
                checked.add(number.longValue());
            } else if (arg instanceof String || arg instanceof ChatComponentWrappedTranslation) {
                checked.add(arg);
            } else {
                throw new IllegalArgumentException(
                    "Arguments must be strings, numbers or wrapped translations, got " + arg);
            }
        }
        this.key = key;
        this.args = checked;
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
        // Also called on a dedicated server (e.g. for the console) which has no font to measure with
        if (!FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) return text;

        // Hovers get drawn a line at a time and this component's colour is only written before its first line, so write
        // it again at the start of each line the wrapping adds or they come out white
        return ClientTextWrap.wrapToHoverWidth(text)
            .replace("\n", "\n" + getChatStyle().getFormattingCode());
    }

    // Translated with translated args filled in, not wrapped
    private String translate() {
        Object[] filled = new Object[args.size()];
        for (int i = 0; i < filled.length; i++) {
            Object arg = args.get(i);
            if (arg instanceof ChatComponentWrappedTranslation nested) filled[i] = nested.translate();
            else if (arg instanceof Number number) filled[i] = formatNumber(number);
            else filled[i] = arg;
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
            } else if (arg instanceof Long number) {
                buf.writeByte(LONG_ARG);
                buf.writeLong(number);
            } else if (arg instanceof Double number) {
                buf.writeByte(DOUBLE_ARG);
                buf.writeDouble(number);
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
            switch (buf.readByte()) {
                case TRANSLATION_ARG -> {
                    ChatComponentWrappedTranslation nested = new ChatComponentWrappedTranslation();
                    nested.decode(buf);
                    args.add(nested);
                }
                case LONG_ARG -> args.add(buf.readLong());
                case DOUBLE_ARG -> args.add(buf.readDouble());
                default -> args.add(readString(buf));
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
        return "tempora:ChatComponentWrappedTranslation";
    }

    @Override
    protected AbstractChatComponentCustom copySelf() {
        return new ChatComponentWrappedTranslation(key, args.toArray());
    }
}
