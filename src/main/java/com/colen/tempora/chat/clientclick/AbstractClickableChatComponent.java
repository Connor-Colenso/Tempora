package com.colen.tempora.chat.clientclick;

import net.minecraft.util.ChatStyle;

import com.gtnewhorizon.gtnhlib.chat.customcomponents.AbstractChatComponentBuffer;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * GTNHLib custom chat component that runs {@link #onClicked()} on the client when clicked, with whatever data it
 * carries, instead of sending a command to the server. The data travels with the chat message so a click works however
 * old the message is and nothing gets kept on the server for it
 * <p>
 * Chat splits a message into lines of new text components, copying each component's style but not its class. So the
 * click lives in the style, as a {@link ClientClickEvent} installed here on the client the first time the style is read
 * and run by {@code MixinGuiChat}. Never installed on the server so never sent
 * <p>
 * No Tempora classes so it can move to GTNHLib as is. Register subclasses with GTNHLib's
 * {@code ChatComponentCustomRegistry} like any custom component
 */
public abstract class AbstractClickableChatComponent<T extends AbstractClickableChatComponent<T>>
    extends AbstractChatComponentBuffer<T> {

    /** Runs on the client thread when clicked in chat, client code only */
    protected abstract void onClicked();

    @Override
    public ChatStyle getChatStyle() {
        ChatStyle style = super.getChatStyle();
        // Effective side is per thread, so this is false on a singleplayer server thread too
        if (!(style.getChatClickEvent() instanceof ClientClickEvent) && FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            style.setChatClickEvent(new ClientClickEvent(this::onClicked));
        }
        return style;
    }
}
