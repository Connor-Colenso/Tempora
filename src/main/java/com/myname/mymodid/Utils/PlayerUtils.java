package com.myname.mymodid.Utils;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public class PlayerUtils {

    public static String UUIDToName(String UUIDString) {
        // To get a name from a UUID:
        UUID playerUUID = UUID.fromString(UUIDString); // field_152366_X

        GameProfile gameprofile = MinecraftServer.getServer().func_152358_ax().func_152652_a(playerUUID);

        if (gameprofile != null) {
            return gameprofile.getName();
        }

        return "[COULD_NOT_RESOLVE_UUID] - " + UUIDString;
    }

}
