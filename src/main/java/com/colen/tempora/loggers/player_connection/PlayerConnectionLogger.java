package com.colen.tempora.loggers.player_connection;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericGlobalLogger;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Logs players joining and leaving the server, with where they were and why they left.
 * <p>
 * The server's own list of online players decides what counts. FML makes a player's connection before checking their
 * mods, so someone turned away for missing mods still "leaves" (vanilla even says so in chat) without ever having
 * joined. Only players on the list are logged as leaving, which they are from the moment they have fully joined until
 * they are gone.
 */
public class PlayerConnectionLogger extends GenericGlobalLogger<PlayerConnectionEventInfo> {

    public static final String SERVER_STOPPED = "tempora.event.player_connection.server_stopped";

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.PLAYER_CONNECTION;
    }

    @Override
    public @NotNull String getShortName() {
        return "connection";
    }

    @Override
    public @NotNull PlayerConnectionEventInfo newEventInfo() {
        return new PlayerConnectionEventInfo();
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.MinecraftEvent;
    }

    // Fired once the player is on the server's player list, after the FML handshake.
    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP player)) return;
        logConnection(player, Action.JOIN, "");
    }

    /**
     * From the server's connection to the player closing, whatever closed it, before the player is taken off the
     * player list. Not FML's PlayerLoggedOutEvent, as that does not say why.
     */
    public void onDisconnect(EntityPlayerMP player, IChatComponent reason) {
        if (!isEnabledByConfig) return;
        if (!isOnline(player)) return;

        logConnection(player, Action.LEAVE, reasonText(reason));
    }

    // The loggers close before the server kicks everyone as it stops, so those still online are logged leaving here.
    // After a crash nobody is, so a join with no leave after it means the player was online until the server went
    // down.
    @Override
    protected void beforeServerClose() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;

        for (EntityPlayerMP player : new ArrayList<>(onlinePlayers(server))) {
            logConnection(player, Action.LEAVE, SERVER_STOPPED);
        }
    }

    private void logConnection(EntityPlayerMP player, Action action, String reason) {
        if (!isEnabledByConfig) return;

        PlayerConnectionEventInfo eventInfo = new PlayerConnectionEventInfo();
        eventInfo.action = action.getDbId();
        eventInfo.playerUUID = player.getUniqueID()
            .toString();
        eventInfo.reason = reason;
        eventInfo.dimensionID = player.dimension;
        eventInfo.x = player.posX;
        eventInfo.y = player.posY;
        eventInfo.z = player.posZ;

        logEvent(eventInfo);
    }

    private static boolean isOnline(EntityPlayerMP player) {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && server.getConfigurationManager() != null && onlinePlayers(server).contains(player);
    }

    @SuppressWarnings("unchecked")
    private static List<EntityPlayerMP> onlinePlayers(MinecraftServer server) {
        return server.getConfigurationManager().playerEntityList;
    }

    // A translation key when vanilla gave one with nothing to fill in, e.g. disconnect.timeout, so it shows in the
    // reader's language. Otherwise, as for a kick message, the text.
    private static String reasonText(IChatComponent reason) {
        if (reason instanceof ChatComponentTranslation translation && translation.getFormatArgs().length == 0) {
            return translation.getKey();
        }
        return reason.getUnformattedText();
    }

    public enum Action {

        JOIN(0, "tempora.event.player_connection.join"),
        LEAVE(1, "tempora.event.player_connection.leave");

        private final int dbId;
        private final String langKey;

        Action(int dbId, String langKey) {
            this.dbId = dbId;
            this.langKey = langKey;
        }

        public int getDbId() {
            return dbId;
        }

        public String langKey() {
            return langKey;
        }

        public static Action fromDbId(int dbId) {
            for (Action action : values()) {
                if (action.dbId == dbId) return action;
            }
            return null;
        }
    }
}
