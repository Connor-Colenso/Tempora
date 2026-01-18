package com.colen.tempora.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.UsernameCache;

public class PlayerUtils {

    public static final String UNKNOWN_PLAYER_NAME = "tempora.unknown.player";
    public static final String UNKNOWN_CAUSE = "tempora.unknown.cause"; // todo use
    public static final String ERROR = "tempora.unknown.error";

    public static IChatComponent entityUUIDChatComponent(String uuid) {
        IChatComponent clickToCopy = new ChatComponentTranslation("tempora.uuid.click_to_copy");
        clickToCopy.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        return new ChatComponentText("[UUID]").setChatStyle(
            new ChatStyle().setColor(EnumChatFormatting.AQUA)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, uuid))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, clickToCopy)));
    }

    public static String UUIDToName(String UUIDString) {
        if (!isUUID(UUIDString)) return UUIDString;

        String userName = UsernameCache.getLastKnownUsername(UUID.fromString(UUIDString));
        if (userName == null) return UNKNOWN_PLAYER_NAME;

        return userName;
    }

    /**
     * Generates a chat component showing the player's name,
     * with a hover showing the UUID. Invalid UUIDs will return a relevant translation code.
     */
    public static IChatComponent playerNameFromUUID(String uuid) {
        if (uuid == null) {
            return new ChatComponentTranslation(ERROR);
        } else if (uuid.equals(UNKNOWN_PLAYER_NAME) || uuid.equals(UNKNOWN_CAUSE)) {
            return new ChatComponentTranslation(uuid);
        }

        // Use UUIDToName for consistency
        String playerName = UUIDToName(uuid);

        IChatComponent nameComponent = new ChatComponentText(playerName);

        // Hover text showing the UUID
        IChatComponent hoverComponent = new ChatComponentTranslation("tempora.uuid.display", uuid);
        hoverComponent.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        // Attach hover event
        nameComponent.getChatStyle()
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponent));

        return nameComponent;
    }

    public static boolean isPlayerOp(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP playerMP)) return false;

        MinecraftServer server = playerMP.mcServer;
        return server.getConfigurationManager()
            .func_152596_g(player.getGameProfile());
    }

    /**
     * Sends a translated chat message to every online operator.
     *
     * @param translationKey the translation key in the language file
     * @param params         optional arguments that will be substituted into the translation
     */
    public static void sendMessageToOps(String translationKey, Object... params) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

        ServerConfigurationManager scm = server.getConfigurationManager();
        if (scm == null) return;

        // Iterate over every connected player.
        for (EntityPlayerMP obj : scm.playerEntityList) {
            if (isPlayerOp(obj)) {
                IChatComponent chat = new ChatComponentTranslation(translationKey, params);
                obj.addChatMessage(chat);
            }
        }
    }

    // Warning! This is tab completion for every player who has ever been on the server, not just those online!
    public static List<String> getTabCompletionForPlayerNames(String prefix) {
        List<String> completions = new ArrayList<>();
        if (prefix == null) prefix = "";

        Map<UUID, String> map = UsernameCache.getMap();
        String lowerPrefix = prefix.toLowerCase();

        for (String playerName : map.values()) {
            if (playerName.toLowerCase()
                .startsWith(lowerPrefix)) {
                completions.add(playerName);
            }
        }

        return completions;
    }

    // Utils
    private static final Pattern UUID_PATTERN = Pattern
        .compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private static boolean isUUID(String str) {
        return str != null && UUID_PATTERN.matcher(str)
            .matches();
    }

}
