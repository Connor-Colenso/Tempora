package com.colen.tempora.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.UsernameCache;

import org.jetbrains.annotations.Nullable;

public class PlayerUtils {

    public static final String UNKNOWN_PLAYER_NAME = "tempora.unknown.player";
    public static final String UNKNOWN_CAUSE = "tempora.unknown.cause";
    public static final String ERROR = "tempora.unknown.error";

    public static String UUIDToName(String UUIDString) {
        if (!isUUID(UUIDString)) return UUIDString;

        String userName = UsernameCache.getLastKnownUsername(UUID.fromString(UUIDString));
        if (userName == null) return UNKNOWN_PLAYER_NAME;

        return userName;
    }

    // Chat component with the player's name and their UUID on hover. Invalid UUIDs return a matching translation key
    public static IChatComponent playerNameFromUUID(String uuid) {
        if (uuid == null) {
            return new ChatComponentTranslation(ERROR);
        } else if (uuid.equals(UNKNOWN_PLAYER_NAME) || uuid.equals(UNKNOWN_CAUSE)) {
            return new ChatComponentTranslation(uuid);
        } else if (!isUUID(uuid)) {
            // Something else caused the event, e.g. "[EntityZombie]", no UUID to show
            return new ChatComponentText(uuid);
        }

        String playerName = UUIDToName(uuid);

        // A UUID this server's never seen join still shows as unknown, with the UUID on hover
        IChatComponent nameComponent = UNKNOWN_PLAYER_NAME.equals(playerName)
            ? new ChatComponentTranslation(UNKNOWN_PLAYER_NAME)
            : new ChatComponentText(playerName);

        IChatComponent hoverComponent = new ChatComponentTranslation("tempora.uuid.display", uuid);
        hoverComponent.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        nameComponent.getChatStyle()
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponent));

        return nameComponent;
    }

    // Sends a translated chat message to every online player who can use /tempora (see CommandUtils.mayUseTempora).
    // Done this way so ServerUtilities permissions granted for Tempora also get these messages
    public static void sendMessageToAuthorisedTemporaUsers(String translationKey, Object... params) {
        sendMessageToAuthorisedTemporaUsers(new ChatComponentTranslation(translationKey, params));
    }

    // Sends a chat message to every online player who can use /tempora (see CommandUtils.mayUseTempora)
    public static void sendMessageToAuthorisedTemporaUsers(IChatComponent message) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

        ServerConfigurationManager scm = server.getConfigurationManager();
        if (scm == null) return;

        for (EntityPlayerMP player : scm.playerEntityList) {
            if (CommandUtils.mayUseTempora(player)) {
                player.addChatMessage(message);
            }
        }
    }

    // Careful, this tab completes every player who's ever been on the server, not just online ones
    public static List<String> getTabCompletionForPlayerNames(String prefix) {
        List<String> completions = new ArrayList<>();
        if (prefix == null) prefix = "";

        Map<UUID, String> map = UsernameCache.getMap();
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);

        for (String playerName : map.values()) {
            if (playerName.toLowerCase(Locale.ROOT)
                .startsWith(lowerPrefix)) {
                completions.add(playerName);
            }
        }

        return completions;
    }

    // Takes a UUID or the name of anyone who's ever joined, ignoring case. Null if unknown
    public static @Nullable String uuidFromName(String nameOrUUID) {
        if (isUUID(nameOrUUID)) return nameOrUUID;

        for (Map.Entry<UUID, String> entry : UsernameCache.getMap()
            .entrySet()) {
            if (entry.getValue()
                .equalsIgnoreCase(nameOrUUID)) {
                return entry.getKey()
                    .toString();
            }
        }
        return null;
    }

    private static final Pattern UUID_PATTERN = Pattern
        .compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    public static boolean isUUID(String str) {
        return str != null && UUID_PATTERN.matcher(str)
            .matches();
    }

}
