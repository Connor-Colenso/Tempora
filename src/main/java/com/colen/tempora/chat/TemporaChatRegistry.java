package com.colen.tempora.chat;

import com.gtnewhorizon.gtnhlib.chat.ChatComponentCustomRegistry;

public class TemporaChatRegistry {

    public static void register() {
        ChatComponentCustomRegistry.register(ChatComponentTimeRelative::new);
        ChatComponentCustomRegistry.register(ChatComponentTimeAbsolute::new);
    }
}
