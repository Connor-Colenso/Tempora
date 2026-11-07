package com.colen.tempora.mixins.client;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.colen.tempora.chat.clientclick.ClientClickEvent;

/**
 * Runs a {@link ClientClickEvent} when its chat text is left clicked, before vanilla handles the click. Finds the
 * clicked text the same way vanilla's mouseClicked does. Self contained apart from the event so it can move to GTNHLib
 * with it
 */
@Mixin(GuiChat.class)
public abstract class MixinGuiChat extends GuiScreen {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tempora$runClientClick(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (mouseButton != 0 || !this.mc.gameSettings.chatLinks) return;

        IChatComponent clicked = this.mc.ingameGUI.getChatGUI()
            .func_146236_a(Mouse.getX(), Mouse.getY());
        if (clicked == null) return;

        ClickEvent click = clicked.getChatStyle()
            .getChatClickEvent();
        if (click instanceof ClientClickEvent clientClick) {
            clientClick.run();
            ci.cancel();
        }
    }
}
