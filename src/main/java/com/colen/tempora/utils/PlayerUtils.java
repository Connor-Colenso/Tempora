package com.colen.tempora.utils;

import static com.colen.tempora.TemporaUtils.UNKNOWN_PLAYER_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.UsernameCache;

import com.mojang.authlib.GameProfile;

public class PlayerUtils {

    public static String UUIDToName(String UUIDString) {
        if (UUIDString.equals(UNKNOWN_PLAYER_NAME)) return UNKNOWN_PLAYER_NAME;
        if (!isUUID(UUIDString)) return UUIDString;

        // To get a name from a UUID:
        UUID playerUUID = UUID.fromString(UUIDString); // field_152366_X

        GameProfile gameprofile = MinecraftServer.getServer()
            .func_152358_ax()
            .func_152652_a(playerUUID);

        if (gameprofile != null) {
            return gameprofile.getName();
        } else {
            return UNKNOWN_PLAYER_NAME;
        }
    }

    private static final Pattern UUID_PATTERN = Pattern
        .compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    public static boolean isUUID(String str) {
        return str != null && UUID_PATTERN.matcher(str)
            .matches();
    }

    public static boolean isPlayerOp(EntityPlayer player) {
        if (isSinglePlayer()) return true; // SP override.

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return false;

        ServerConfigurationManager scm = server.getConfigurationManager();
        if (scm == null) return false;

        // func_152596_g is the obfuscated method that checks if the player is OP
        // Yes it is dumb that this is seemingly the only way to do this in this version!
        return scm.func_152596_g(player.getGameProfile());
    }

    public static boolean isSinglePlayer() {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && !server.isDedicatedServer();
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

    @Nullable
    public static String uuidForName(String name) {
        Map<UUID, String> map = UsernameCache.getMap();
        for (Map.Entry<UUID, String> entry : map.entrySet()) {
            if (entry.getValue()
                .equalsIgnoreCase(name)) {
                return entry.getKey()
                    .toString();
            }
        }
        return null;
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
}
