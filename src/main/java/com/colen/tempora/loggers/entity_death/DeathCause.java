package com.colen.tempora.loggers.entity_death;

import static com.colen.tempora.utils.GenericUtils.entityRegistryName;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_CAUSE;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.colen.tempora.chat.EventChat;
import com.colen.tempora.utils.PlayerUtils;

/**
 * What killed a mob or a player, as the death loggers store it. A player killer is kept in a player column of its own,
 * so /tempora player and p: find what they killed. {@code killedBy} is anything else in brackets: a mob by its
 * EntityList name, e.g. [Zombie], or a damage type such as [fall] or [inFire]. For a player kill it is how they did it,
 * e.g. [player] or [arrow].
 */
public final class DeathCause {

    public final String killerPlayer;
    public final String killedBy;

    private DeathCause(String killerPlayer, String killedBy) {
        this.killerPlayer = killerPlayer;
        this.killedBy = killedBy;
    }

    public static DeathCause of(@Nullable DamageSource source) {
        if (source == null) return new DeathCause(UNKNOWN_PLAYER_NAME, UNKNOWN_CAUSE);

        Entity trueSource = source.getEntity();
        if (trueSource instanceof EntityPlayer player) {
            return new DeathCause(
                player.getUniqueID()
                    .toString(),
                "[" + source.damageType + "]");
        }
        if (trueSource != null) return new DeathCause(UNKNOWN_PLAYER_NAME, "[" + entityRegistryName(trueSource) + "]");
        return new DeathCause(UNKNOWN_PLAYER_NAME, "[" + source.damageType + "]");
    }

    /** Who killed it, a player or a mob, or null if neither did. */
    public static @Nullable IChatComponent killer(String killerPlayer, String killedBy) {
        if (PlayerUtils.isUUID(killerPlayer)) return EventChat.player(killerPlayer);

        String cause = unbracketed(killedBy);
        return cause != null && isEntity(cause) ? EventChat.entityName(cause) : null;
    }

    /** What it died of when nobody killed it, e.g. "in fire", or null if that is not known. */
    public static @Nullable IChatComponent damage(String killedBy) {
        String cause = unbracketed(killedBy);
        if (cause == null) return null;

        // inFire -> in fire.
        String damage = cause.replaceAll("([a-z])([A-Z])", "$1 $2")
            .toLowerCase(Locale.ROOT);
        return EventChat.text(damage, EnumChatFormatting.WHITE);
    }

    private static @Nullable String unbracketed(String killedBy) {
        if (killedBy == null || killedBy.length() <= 2 || !killedBy.startsWith("[") || !killedBy.endsWith("]")) {
            return null;
        }
        return killedBy.substring(1, killedBy.length() - 1);
    }

    // Vanilla's damage types start lower case and have no translated entity name, mobs do one or the other.
    private static boolean isEntity(String cause) {
        return Character.isUpperCase(cause.charAt(0)) || StatCollector.canTranslate("entity." + cause + ".name");
    }
}
