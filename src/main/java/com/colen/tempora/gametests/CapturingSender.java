package com.colen.tempora.gametests;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

/**
 * A command sender that keeps the chat it is sent, so tests can run lookups the way a player or the console would and
 * check what came back. Each test should use its own name, as Tempora allows one lookup at a time per sender.
 */
public class CapturingSender implements ICommandSender {

    private final String name;
    private final World world;
    private final List<IChatComponent> messages = new ArrayList<>();

    public CapturingSender(String name, World world) {
        this.name = name;
        this.world = world;
    }

    public List<IChatComponent> getMessages() {
        return messages;
    }

    // Every message as plain text, one per line.
    public String text() {
        StringBuilder text = new StringBuilder();
        for (IChatComponent message : messages) {
            text.append(message.getUnformattedText())
                .append('\n');
        }
        return text.toString();
    }

    @Override
    public String getCommandSenderName() {
        return name;
    }

    @Override
    public IChatComponent func_145748_c_() {
        return new ChatComponentText(name);
    }

    @Override
    public void addChatMessage(IChatComponent message) {
        messages.add(message);
    }

    @Override
    public boolean canCommandSenderUseCommand(int permissionLevel, String command) {
        return true;
    }

    @Override
    public ChunkCoordinates getPlayerCoordinates() {
        return new ChunkCoordinates(0, 0, 0);
    }

    @Override
    public World getEntityWorld() {
        return world;
    }
}
