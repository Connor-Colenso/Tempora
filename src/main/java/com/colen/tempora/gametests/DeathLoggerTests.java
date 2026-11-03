package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.LookupTests.assertShown;
import static com.colen.tempora.gametests.PositionalLoggerTests.assertCount;
import static com.colen.tempora.gametests.PositionalLoggerTests.eventsAtPlayer;
import static com.colen.tempora.gametests.PositionalLoggerTests.uuid;
import static com.colen.tempora.gametests.TestSupport.assertWellFormed;
import static com.colen.tempora.gametests.TestSupport.fakePlayer;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.commands.LookupCommand;
import com.colen.tempora.loggers.generic.FoundEvents;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.player_death.PlayerDeathEventInfo;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeWindow;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import cpw.mods.fml.common.eventhandler.EventBus;

/** Player deaths go to the player death logger and mob deaths to the mob one, each with what killed them. */
@GameTestHolder(Tempora.MODID)
public class DeathLoggerTests {

    private static final int WRITE_TICKS = 100;
    private static final int TIMEOUT_TICKS = 200;

    private static final TestPos BLOCK = at(2, 1, 2);

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void playerDeathIsLogged(GameTestHelper helper) {
        FakePlayer player = fakePlayer(helper, "player_death");
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "playerDeathIsLogged");
        Probe<List<PositionalEventInfo>> mobDeaths = new Probe<>(helper, "playerDeathIsLogged.mob");
        boolean keepInventory = helper.getWorld()
            .getGameRules()
            .getGameRuleBooleanValue("keepInventory");

        helper.startSequence()
            .thenExecute("a zombie kills a player on level 7", () -> {
                player.experienceLevel = 7;
                EntityZombie zombie = new EntityZombie(helper.getWorld());
                die(helper, player, DamageSource.causeMobDamage(zombie));
            })
            .thenWaitUntil(
                "death is logged",
                WRITE_TICKS,
                () -> assertCount(
                    helper,
                    probe.poll(() -> eventsAtPlayer(helper, TemporaEvents.playerDeathLogger, player)),
                    1))
            .thenExecute("event reads back intact", () -> {
                PlayerDeathEventInfo event = (PlayerDeathEventInfo) probe.latest()
                    .get(0);
                helper.assertEquals(uuid(player), event.playerUUID, "playerUUID");
                helper.assertEquals(PlayerUtils.UNKNOWN_PLAYER_NAME, event.killerPlayer, "killerPlayer");
                helper.assertEquals("[Zombie]", event.killedBy, "killedBy");
                helper.assertEquals(7L, event.xpLevel, "xpLevel");
                helper.assertEquals(keepInventory, event.keepInventory, "keepInventory");
                assertWellFormed(helper, TemporaEvents.playerDeathLogger, probe.latest());
            })
            .thenWaitUntil(
                "the mob death logger left it out",
                WRITE_TICKS,
                () -> assertCount(
                    helper,
                    mobDeaths.poll(() -> eventsAtPlayer(helper, TemporaEvents.entityDeathLogger, player)),
                    0))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void playerKillerIsLogged(GameTestHelper helper) {
        FakePlayer victim = fakePlayer(helper, "pvp_victim");
        FakePlayer killer = fakePlayer(helper, "pvp_killer");
        Probe<List<PositionalEventInfo>> probe = new Probe<>(helper, "playerKillerIsLogged");

        helper.startSequence()
            .thenExecute("one player kills another", () -> die(helper, victim, DamageSource.causePlayerDamage(killer)))
            .thenWaitUntil(
                "death is logged",
                WRITE_TICKS,
                () -> assertCount(
                    helper,
                    probe.poll(() -> eventsAtPlayer(helper, TemporaEvents.playerDeathLogger, victim)),
                    1))
            .thenExecute("the killer is stored as a player", () -> {
                PlayerDeathEventInfo event = (PlayerDeathEventInfo) probe.latest()
                    .get(0);
                helper.assertEquals(uuid(victim), event.playerUUID, "playerUUID");
                helper.assertEquals(uuid(killer), event.killerPlayer, "killerPlayer");
                helper.assertEquals("[player]", event.killedBy, "killedBy");
            })
            .thenSucceed();
    }

    // p: on mob deaths goes by the player who killed the mob.
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void playerFilterFindsTheirKills(GameTestHelper helper) {
        FakePlayer alice = fakePlayer(helper, "kill_alice");
        FakePlayer bob = fakePlayer(helper, "kill_bob");
        TestPos pos = helper.absolute(BLOCK);
        int dim = helper.getWorld().provider.dimensionId;
        int n = LookupCommand.DEFAULT_COUNT;
        Probe<FoundEvents<?>> onlyAlice = new Probe<>(helper, "playerFilterFindsTheirKills.onlyAlice");
        Probe<FoundEvents<?>> both = new Probe<>(helper, "playerFilterFindsTheirKills.both");

        helper.startSequence()
            .thenExecute("alice and bob each kill a pig", () -> {
                for (FakePlayer player : new FakePlayer[] { alice, bob }) {
                    EntityPig pig = new EntityPig(helper.getWorld());
                    pig.setPosition(pos.x() + 0.5, pos.y(), pos.z() + 0.5);
                    die(helper, pig, DamageSource.causePlayerDamage(player));
                }
            })
            .thenWaitUntil(
                "l:mobdeath p:alice finds hers",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    onlyAlice.poll(
                        () -> TemporaEvents.entityDeathLogger.findEventsNear(
                            dim,
                            pos.x(),
                            pos.y(),
                            pos.z(),
                            1,
                            TimeWindow.allTime(),
                            PlayerFilter.parse(uuid(alice)),
                            n)),
                    1))
            .thenWaitUntil(
                "l:mobdeath p:alice,bob finds both",
                WRITE_TICKS,
                () -> assertShown(
                    helper,
                    both.poll(
                        () -> TemporaEvents.entityDeathLogger.findEventsNear(
                            dim,
                            pos.x(),
                            pos.y(),
                            pos.z(),
                            1,
                            TimeWindow.allTime(),
                            PlayerFilter.parse(uuid(alice) + "," + uuid(bob)),
                            n)),
                    2))
            .thenSucceed();
    }

    // The event a dying entity fires, as fake players cannot die and the test server removes animals. Posted on a bus
    // of the death loggers' own, as other mods' handlers act on deaths (graves) or expect a logged-in player.
    private static void die(GameTestHelper helper, EntityLivingBase entity, DamageSource source) {
        EventBus bus = new EventBus();
        bus.register(TemporaEvents.entityDeathLogger);
        bus.register(TemporaEvents.playerDeathLogger);
        helper.assertFalse(bus.post(new LivingDeathEvent(entity, source)), "the death was cancelled");
    }
}
