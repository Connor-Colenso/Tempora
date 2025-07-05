package com.colen.tempora.utils;

import static com.colen.tempora.TemporaUtils.UNKNOWN_PLAYER_NAME;

import java.util.UUID;
import java.util.regex.Pattern;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.server.management.UserListOps;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

public class PlayerUtils {

    public static String UUIDToName(String UUIDString) {
        if (!UUIDString.equals(UNKNOWN_PLAYER_NAME)) {
            // To get a name from a UUID:
            UUID playerUUID = UUID.fromString(UUIDString); // field_152366_X

            GameProfile gameprofile = MinecraftServer.getServer()
                .func_152358_ax()
                .func_152652_a(playerUUID);

            if (gameprofile != null) {
                return gameprofile.getName();
            }
        }

        return "[COULD_NOT_RESOLVE_UUID] - " + UUIDString;
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
}
