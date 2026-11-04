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
    public static final String UNKNOWN_CAUSE = "tempora.unknown.cause"; // todo use
    public static final String ERROR = "tempora.unknown.error";

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
        } else if (!isUUID(uuid)) {
            // Something else that caused the event, e.g. "[EntityZombie]", which has no UUID to show.
            return new ChatComponentText(uuid);
        }

        // Use UUIDToName for consistency
        String playerName = UUIDToName(uuid);

        // A UUID this server has never seen join is still shown as unknown, with the UUID on hover.
        IChatComponent nameComponent = UNKNOWN_PLAYER_NAME.equals(playerName)
            ? new ChatComponentTranslation(UNKNOWN_PLAYER_NAME)
            : new ChatComponentText(playerName);

        // Hover text showing the UUID
        IChatComponent hoverComponent = new ChatComponentTranslation("tempora.uuid.display", uuid);
        hoverComponent.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        // Attach hover event
        nameComponent.getChatStyle()
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponent));

        return nameComponent;
    }

    /**
     * Sends a translated chat message to every online player who may use /tempora (see
     * {@link CommandUtils#mayUseTempora}). This is structured in this manner so that ServerUtilities permissions that
     * are granted for Tempora also notify you of these messages.
     *
     * @param translationKey the translation key in the language file
     * @param params         optional arguments that will be substituted into the translation
     */
    public static void sendMessageToAuthorisedTemporaUsers(String translationKey, Object... params) {
        sendMessageToAuthorisedTemporaUsers(new ChatComponentTranslation(translationKey, params));
    }

    /** Sends a chat message to every online player who may use /tempora (see {@link CommandUtils#mayUseTempora}). */
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

    // Warning! This is tab completion for every player who has ever been on the server, not just those online!
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

    // Accepts a UUID, or the name of anyone who has ever joined the server, ignoring case. Null if unknown.
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

    // Utils
    private static final Pattern UUID_PATTERN = Pattern
        .compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    public static boolean isUUID(String str) {
        return str != null && UUID_PATTERN.matcher(str)
            .matches();
    }

}
