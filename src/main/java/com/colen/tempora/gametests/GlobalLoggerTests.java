package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.TestSupport.assertWellFormed;
import static com.colen.tempora.gametests.TestSupport.eventsSince;
import static com.colen.tempora.gametests.TestSupport.fakePlayer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.FakePlayer;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.player_connection.PlayerConnectionEventInfo;
import com.colen.tempora.loggers.player_connection.PlayerConnectionLogger;
import com.colen.tempora.loggers.weather.WeatherEventInfo;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.Weather;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import cpw.mods.fml.common.gameevent.PlayerEvent;

/** The global loggers, whose events belong to the whole server rather than a place. */
@GameTestHolder(Tempora.MODID)
public class GlobalLoggerTests {

    // Rain takes a while to set in: the world counts as raining once its rain strength passes 0.2.
    @GameTest(timeoutTicks = 300)
    public static void weatherChangeIsLogged(GameTestHelper helper) {
        int dim = helper.getWorld().provider.dimensionId;
        long since = System.currentTimeMillis();
        Probe<List<GenericEventInfo>> probe = new Probe<>(helper, "weatherChangeIsLogged");

        helper.startSequence()
            // A tick of clear weather first, so the logger has seen it clear before it starts raining.
            .thenExecute("clear", () -> helper.setWeather(Weather.CLEAR))
            .thenIdle(5)
            .thenExecute("start raining", () -> helper.setWeather(Weather.RAIN))
            .thenWaitUntil(
                "the change to rain is logged",
                250,
                () -> helper.assertFalse(
                    rainEvents(probe.poll(() -> eventsSince(TemporaEvents.weatherLogger, since)), dim).isEmpty(),
                    "no change to rain logged"))
            .thenExecute("event reads back intact", () -> {
                WeatherEventInfo event = rainEvents(probe.latest(), dim).get(0);
                helper.assertEquals("CLEAR", event.previousWeather, "previousWeather");
                assertWellFormed(helper, TemporaEvents.weatherLogger, probe.latest());
            })
            .thenSucceed();
    }

    // FML makes a player's connection before checking their mods, so a player turned away for missing mods still
    // disconnects. They were never on the player list, so nothing is logged.
    @GameTest(timeoutTicks = 200)
    public static void leaveWithoutJoiningIsNotLogged(GameTestHelper helper) {
        FakePlayer player = fakePlayer(helper, "never_joined");
        String uuid = player.getUniqueID()
            .toString();
        long since = System.currentTimeMillis();
        Probe<List<GenericEventInfo>> probe = new Probe<>(helper, "leaveWithoutJoiningIsNotLogged");

        helper.startSequence()
            .thenExecute(
                "disconnect without joining",
                () -> TemporaEvents.playerConnectionLogger
                    .onDisconnect(player, new ChatComponentTranslation("disconnect.endOfStream")))
            // Long enough for the write, had there been one.
            .thenIdle(60)
            .thenWaitUntil(
                "nothing was logged",
                100,
                () -> helper.assertTrue(
                    connectionEvents(probe.poll(() -> eventsSince(TemporaEvents.playerConnectionLogger, since)), uuid)
                        .isEmpty(),
                    "a leave was logged for a player who never joined"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 200)
    public static void leaveIsLogged(GameTestHelper helper) {
        FakePlayer player = fakePlayer(helper, "leaves");
        String uuid = player.getUniqueID()
            .toString();
        long since = System.currentTimeMillis();
        Probe<List<GenericEventInfo>> probe = new Probe<>(helper, "leaveIsLogged");

        helper.startSequence()
            .thenExecute("disconnect while on the player list", () -> {
                // On the list only for this call, so nothing else on the server thread sees it. A fake player has no
                // connection for anything to send to.
                @SuppressWarnings("unchecked")
                List<EntityPlayerMP> online = MinecraftServer.getServer()
                    .getConfigurationManager().playerEntityList;
                online.add(player);
                try {
                    TemporaEvents.playerConnectionLogger
                        .onDisconnect(player, new ChatComponentTranslation("disconnect.timeout"));
                } finally {
                    online.remove(player);
                }
            })
            .thenWaitUntil(
                "the leave is logged",
                100,
                () -> helper.assertEquals(
                    1,
                    connectionEvents(probe.poll(() -> eventsSince(TemporaEvents.playerConnectionLogger, since)), uuid)
                        .size(),
                    "leaves logged"))
            .thenExecute("event reads back intact", () -> {
                PlayerConnectionEventInfo event = connectionEvents(probe.latest(), uuid).get(0);
                helper.assertEquals(PlayerConnectionLogger.Action.LEAVE.getDbId(), event.action, "action");
                helper.assertEquals("disconnect.timeout", event.reason, "reason");
                helper.assertEquals(player.dimension, event.dimensionID, "dimensionID");
                assertWellFormed(helper, TemporaEvents.playerConnectionLogger, probe.latest());
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 200)
    public static void joinIsLogged(GameTestHelper helper) {
        FakePlayer player = fakePlayer(helper, "joins");
        String uuid = player.getUniqueID()
            .toString();
        long since = System.currentTimeMillis();
        Probe<List<GenericEventInfo>> probe = new Probe<>(helper, "joinIsLogged");

        helper.startSequence()
            .thenExecute(
                "join",
                () -> TemporaEvents.playerConnectionLogger
                    .onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player)))
            .thenWaitUntil(
                "the join is logged",
                100,
                () -> helper.assertEquals(
                    1,
                    connectionEvents(probe.poll(() -> eventsSince(TemporaEvents.playerConnectionLogger, since)), uuid)
                        .size(),
                    "joins logged"))
            .thenExecute("event reads back intact", () -> {
                PlayerConnectionEventInfo event = connectionEvents(probe.latest(), uuid).get(0);
                helper.assertEquals(PlayerConnectionLogger.Action.JOIN.getDbId(), event.action, "action");
                helper.assertEquals(player.posX, event.x, 0.001, "x");
                assertWellFormed(helper, TemporaEvents.playerConnectionLogger, probe.latest());
            })
            .thenSucceed();
    }

    private static List<PlayerConnectionEventInfo> connectionEvents(List<GenericEventInfo> events, String uuid) {
        List<PlayerConnectionEventInfo> matching = new ArrayList<>();
        for (GenericEventInfo event : events) {
            PlayerConnectionEventInfo connection = (PlayerConnectionEventInfo) event;
            if (uuid.equals(connection.playerUUID)) matching.add(connection);
        }
        return matching;
    }

    private static List<WeatherEventInfo> rainEvents(List<GenericEventInfo> events, int dim) {
        List<WeatherEventInfo> rain = new ArrayList<>();
        for (GenericEventInfo event : events) {
            WeatherEventInfo weather = (WeatherEventInfo) event;
            if (weather.dimensionID == dim && "RAIN".equals(weather.weather)) rain.add(weather);
        }
        return rain;
    }
}
