package com.colen.tempora.chat.clientclick;

import net.minecraft.util.ChatStyle;

import com.gtnewhorizon.gtnhlib.chat.customcomponents.AbstractChatComponentBuffer;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * A GTNHLib custom chat component that runs {@link #onClicked()} on the client when clicked, with whatever data it
 * carries, rather than sending a command to the server. The data travels with the chat message, so a click still works
 * however long ago the message arrived, and nothing has to be kept on the server for it.
 * <p>
 * Chat splits a message into lines of new text components, copying each component's style but not its class. So the
 * click is carried in the style, as a {@link ClientClickEvent} installed here on the client the first time the style is
 * read, and run by {@code MixinGuiChat}. On the server it is never installed, so it is never sent.
 * <p>
 * Self-contained, with no Tempora classes, so it can move to GTNHLib unchanged. Register subclasses with GTNHLib's
 * {@code ChatComponentCustomRegistry} like any custom component.
 */
public abstract class AbstractClickableChatComponent<T extends AbstractClickableChatComponent<T>>
    extends AbstractChatComponentBuffer<T> {

    /** Runs on the client thread when the component is clicked in chat. Only client code belongs here. */
    protected abstract void onClicked();

    @Override
    public ChatStyle getChatStyle() {
        ChatStyle style = super.getChatStyle();
        // The effective side is per thread, so this is false on a singleplayer world's server thread too.
        if (!(style.getChatClickEvent() instanceof ClientClickEvent) && FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            style.setChatClickEvent(new ClientClickEvent(this::onClicked));
        }
        return style;
    }
}
