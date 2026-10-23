package com.colen.tempora.chat.clientclick;

import net.minecraft.event.ClickEvent;

/**
 * A click that runs code on the client instead of one of vanilla's click actions, such as printing something only that
 * player sees, or starting something drawn in the world. It is never sent over the network: an
 * {@link AbstractClickableChatComponent} creates it on the client, and {@code MixinGuiChat} runs it when its text is
 * clicked in chat.
 * <p>
 * Self-contained, with no Tempora classes, so it can move to GTNHLib unchanged.
 */
public final class ClientClickEvent extends ClickEvent {

    private final Runnable action;

    /**
     * {@code action} runs on the client thread. Without the mixin (e.g. another mod overrides chat clicks), the click
     * falls back to suggesting an empty command, which does nothing.
     */
    public ClientClickEvent(Runnable action) {
        super(Action.SUGGEST_COMMAND, "");
        this.action = action;
    }

    public void run() {
        action.run();
    }
}
