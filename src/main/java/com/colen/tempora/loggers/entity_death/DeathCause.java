package com.colen.tempora.loggers.entity_death;

import static com.colen.tempora.utils.GenericUtils.entityRegistryName;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.chat.EventChat;
import com.colen.tempora.utils.PlayerUtils;

// What killed a mob or player, as the death loggers store it. A player killer goes in its own player column so /tempora
// player and p: find what they killed, a mob killer by EntityList name like Zombie in a MobNames column so f: finds it.
// damageType is how it died, e.g. fall, inFire, or player and arrow for kills. Empty when not known
public final class DeathCause {

    public final String killerPlayerUUID;
    public final String killerMob;
    public final String damageType;

    private DeathCause(String killerPlayerUUID, String killerMob, String damageType) {
        this.killerPlayerUUID = killerPlayerUUID;
        this.killerMob = killerMob;
        this.damageType = damageType;
    }

    public static DeathCause of(@Nullable DamageSource source) {
        if (source == null) return new DeathCause(UNKNOWN_PLAYER_NAME, "", "");

        Entity trueSource = source.getEntity();
        if (trueSource instanceof EntityPlayer player) {
            return new DeathCause(
                player.getUniqueID()
                    .toString(),
                "",
                source.damageType);
        }
        if (trueSource != null) {
            return new DeathCause(UNKNOWN_PLAYER_NAME, entityRegistryName(trueSource), source.damageType);
        }
        return new DeathCause(UNKNOWN_PLAYER_NAME, "", source.damageType);
    }

    // Who killed it (player or mob), null if neither. A mob is orange since f: finds it
    public static @Nullable IChatComponent killer(String killerPlayerUUID, String killerMob) {
        if (PlayerUtils.isUUID(killerPlayerUUID)) return EventChat.player(killerPlayerUUID);
        return killerMob.isEmpty() ? null : EventChat.searchable(EventChat.entityName(killerMob));
    }

    // What it died of when nobody killed it, e.g. "in fire", null if not known
    public static @Nullable IChatComponent damage(String damageType) {
        return damageType.isEmpty() ? null : EventChat.text(damageText(damageType), EnumChatFormatting.WHITE);
    }

    // inFire -> in fire
    public static String damageText(String damageType) {
        return damageType.replaceAll("([a-z])([A-Z])", "$1 $2")
            .toLowerCase(Locale.ROOT);
    }
}
