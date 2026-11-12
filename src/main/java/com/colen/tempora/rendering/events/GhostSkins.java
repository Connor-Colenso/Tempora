package com.colen.tempora.rendering.events;

import static com.colen.tempora.Tempora.LOG;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Skins for player ghosts of anyone this client hasn't got loaded. Vanilla only asks Mojang for your own skin, a
// profile without textures gets the default one. So this asks Mojang's session server for the signed textures in the
// background, once per player, and the ghost gets made again when they come in
@SideOnly(Side.CLIENT)
final class GhostSkins {

    // Mojang limits how often a profile can be asked for, so a failed one waits before trying again
    private static final long RETRY_MILLIS = 5 * 60_000;
    private static final String TEXTURES = "textures";

    private static final Map<UUID, Fetch> FETCHES = new ConcurrentHashMap<>();

    // One at a time like vanilla's skin manager, a lookup showing lots of players shouldn't flood Mojang
    private static final ExecutorService FETCHER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Tempora-SkinFetch");
        thread.setDaemon(true);
        return thread;
    });

    private GhostSkins() {}

    static boolean hasSkin(GameProfile profile) {
        return profile.getProperties()
            .containsKey(TEXTURES);
    }

    // Profile with the player's skin once Mojang has answered, null until then. Asks if nobody has yet
    static @Nullable GameProfile withSkin(UUID uuid, String name) {
        // Offline mode UUIDs are made from the name (version 3), Mojang has never heard of them
        if (uuid.version() != 4) return null;

        Fetch fetch = FETCHES.get(uuid);
        if (fetch != null && !fetch.shouldRetry()) return fetch.profile;

        Fetch started = new Fetch();
        FETCHES.put(uuid, started);
        FETCHER.submit(() -> started.run(uuid, name));
        return null;
    }

    private static final class Fetch {

        // Written on the fetch thread, read on the render thread
        volatile @Nullable GameProfile profile;
        volatile long failedAt;

        boolean shouldRetry() {
            return failedAt > 0 && System.currentTimeMillis() - failedAt > RETRY_MILLIS;
        }

        void run(UUID uuid, String name) {
            try {
                // Signed, vanilla won't use unsigned textures for someone else
                GameProfile filled = Minecraft.getMinecraft()
                    .func_152347_ac()
                    .fillProfileProperties(new GameProfile(uuid, name), true);

                if (hasSkin(filled)) {
                    // Keeps the name the server sent, same as chat, not Mojang's current one
                    GameProfile withSkin = new GameProfile(uuid, name);
                    withSkin.getProperties()
                        .putAll(filled.getProperties());
                    profile = withSkin;
                    return;
                }
            } catch (RuntimeException e) {
                LOG.warn("Could not get the skin of player {} from Mojang.", uuid, e);
            }
            failedAt = System.currentTimeMillis();
        }
    }
}
