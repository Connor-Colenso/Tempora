package com.colen.tempora.chat.clientclick;

import net.minecraft.event.ClickEvent;

/**
 * Click that runs code on the client instead of one of vanilla's click actions, like printing something only that
 * player sees or starting something drawn in the world. Never sent over the network, an
 * {@link AbstractClickableChatComponent} makes it on the client and {@code MixinGuiChat} runs it when its text is
 * clicked
 * <p>
 * No Tempora classes so it can move to GTNHLib as is
 */
public final class ClientClickEvent extends ClickEvent {

    private final Runnable action;

    /**
     * {@code action} runs on the client thread. Without the mixin (another mod overriding chat clicks) it falls back to
     * suggesting an empty command, which does nothing
     */
    public ClientClickEvent(Runnable action) {
        super(Action.SUGGEST_COMMAND, "");
        this.action = action;
    }

    public void run() {
        action.run();
    }
}
