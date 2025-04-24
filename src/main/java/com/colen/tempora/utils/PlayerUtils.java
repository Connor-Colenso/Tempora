package com.colen.tempora.utils;

import java.util.UUID;
import java.util.regex.Pattern;

import net.minecraft.server.MinecraftServer;

import com.mojang.authlib.GameProfile;

public class PlayerUtils {

    public static String UUIDToName(String UUIDString) {
        // To get a name from a UUID:
        UUID playerUUID = UUID.fromString(UUIDString); // field_152366_X

        GameProfile gameprofile = MinecraftServer.getServer()
            .func_152358_ax()
            .func_152652_a(playerUUID);

        if (gameprofile != null) {
            return gameprofile.getName();
        }

        return "[COULD_NOT_RESOLVE_UUID] - " + UUIDString;
    }

    private static final Pattern UUID_PATTERN = Pattern
        .compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    public static boolean isUUID(String str) {
        return str != null && UUID_PATTERN.matcher(str)
            .matches();
    }

}
