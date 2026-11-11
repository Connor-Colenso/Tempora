package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.TestSupport.answer;
import static com.colen.tempora.gametests.TestSupport.answered;
import static com.colen.tempora.gametests.TestSupport.player;
import static com.colen.tempora.gametests.TestSupport.region;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.achievement.AchievementEventInfo;
import com.colen.tempora.loggers.block_change.BlockChangeEventInfo;
import com.colen.tempora.loggers.command.CommandEventInfo;
import com.colen.tempora.loggers.entity_death.EntityDeathEventInfo;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnEventInfo;
import com.colen.tempora.loggers.generic.FoundEvents;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.names.NamePart;
import com.colen.tempora.loggers.generic.column.names.lookups.AchievementNames;
import com.colen.tempora.loggers.generic.column.names.lookups.ItemNames;
import com.colen.tempora.loggers.generic.column.names.lookups.MobNames;
import com.colen.tempora.loggers.inventory.InventoryEventInfo;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakEventInfo;
import com.colen.tempora.loggers.player_death.PlayerDeathEventInfo;
import com.colen.tempora.regions.LogRule;
import com.colen.tempora.utils.NameRequests;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.ResolvedNames;
import com.colen.tempora.utils.TextFilter;
import com.colen.tempora.utils.TextFold;
import com.colen.tempora.utils.TimeWindow;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.internal.GameTestSequence;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

// The f: filter of /tempora lookup. What it matches in each kind of logger, filters a client sends (which can be
// anything since any client can send one), caps and how text gets folded
@GameTestHolder(Tempora.MODID)
public class TextFilterTests {

    private static final int WRITE_TICKS = 100;
    private static final int TIMEOUT_TICKS = 250;

    // Mob names are the only text that goes into the SQL, so quotes in them have to stay text
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void quotesInMobNamesStayText(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.entityDeathLogger;
        EntityPlayerMP player = player(helper, "name_quotes");
        TestPos pos = helper.absolute(at(1, 1, 1));
        int dim = helper.getWorld().provider.dimensionId;
        Probe<FoundEvents<?>> quoted = new Probe<>(helper, "quotesInMobNamesStayText.quoted");
        Probe<FoundEvents<?>> injected = new Probe<>(helper, "quotesInMobNamesStayText.injected");

        helper.startSequence()
            .thenExecute("a Zom'bie and a Zombie die", () -> {
                TemporaEvents.entityDeathLogger.queueEventInfo(death(helper, pos, "Zom'bie"));
                TemporaEvents.entityDeathLogger.queueEventInfo(death(helper, pos, "Zombie"));
            })
            .thenWaitUntil("a client's Zom'bie finds just that one", WRITE_TICKS, () -> {
                sendEntities(player, "q1", "Zom'bie");
                List<GenericEventInfo> found = events(
                    helper,
                    quoted.poll(() -> near(logger, dim, pos, answered(player, "q1"))));
                helper.assertEquals(1L, (long) found.size(), "deaths found");
                helper.assertEquals("Zom'bie", ((EntityDeathEventInfo) found.get(0)).entityName, "mob found");
            })
            .thenWaitUntil("a name that tries to end the string finds nothing and doesn't break the query", 20, () -> {
                sendEntities(player, "q2", "x') OR 1=1 OR ('", "'; DROP TABLE EntityDeathLogger; --");
                FoundEvents<?> found = injected.poll(() -> near(logger, dim, pos, answered(player, "q2")));
                helper.assertFalse(found.failed(), "the query failed");
                helper.assertEquals(0L, (long) events(helper, found).size(), "deaths found");
            })
            .thenSucceed();
    }

    // A filter goes over the network as is. Read back it has to match the same things
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void sentFilterSurvivesThePacket(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerBlockBreakLogger;
        EntityPlayerMP player = player(helper, "name_packet");
        TestPos pos = helper.absolute(at(2, 1, 1));
        int dim = helper.getWorld().provider.dimensionId;
        int wool = Block.getIdFromBlock(Blocks.wool);
        Probe<FoundEvents<?>> found = new Probe<>(helper, "sentFilterSurvivesThePacket.found");

        ResolvedNames.Builder builder = new ResolvedNames.Builder();
        builder.item(wool, 14);
        builder.item(Block.getIdFromBlock(Blocks.stone), ResolvedNames.ANY_META);
        builder.entity("Pig");
        builder.achievement("achievement.mineWood");
        ByteBuf buf = Unpooled.buffer();
        builder.build()
            .encode(buf);
        answer(player, "красная_шерсть,石头", ResolvedNames.decode(buf));

        helper.startSequence()
            .thenExecute("break red wool, white wool and stone", () -> {
                breakAt(helper, player, pos, Blocks.wool, 14);
                breakAt(helper, player, pos, Blocks.wool, 0);
                breakAt(helper, player, pos, Blocks.stone, 0);
            })
            .thenWaitUntil("the read back filter finds red wool and stone", WRITE_TICKS, () -> {
                TextFilter filter = answered(player, "красная_шерсть,石头");
                helper.assertTrue(
                    TemporaEvents.entitySpawnLogger.textFilterCondition(filter) != null,
                    "the mob in it got lost");
                helper.assertTrue(
                    TemporaEvents.achievementLogger.textFilterCondition(filter) != null,
                    "the achievement in it got lost");
                List<GenericEventInfo> events = events(helper, found.poll(() -> near(logger, dim, pos, filter)));
                helper.assertEquals(2L, (long) events.size(), "breaks found");
            })
            .thenSucceed();
    }

    // Roles go through the packet by class name, with any number of parts and ANY in them. A role the server
    // doesn't have, or values that don't fit a role, get dropped, they can't be in any of its columns
    @GameTest
    public static void modRolesSurviveThePacket(GameTestHelper helper) {
        ResolvedNames.Builder builder = new ResolvedNames.Builder();
        builder.entity("Pig");
        builder.add(DatabaseTests.TestMachineNames.class, 5, NamePart.ANY, 2, "fast");
        ByteBuf buf = Unpooled.buffer();
        builder.build()
            .encode(buf);
        ResolvedNames read = ResolvedNames.decode(buf);
        helper.assertEquals(
            Collections.singleton(Arrays.asList(5, NamePart.ANY, 2, "fast")),
            read.matches(DatabaseTests.TestMachineNames.class),
            "the mod role's machine");
        helper.assertEquals(
            Collections.singleton(Collections.singletonList("Pig")),
            read.matches(MobNames.class),
            "the mob");

        ByteBuf unknown = Unpooled.buffer();
        unknown.writeInt(0);
        unknown.writeInt(2);
        writeRole(unknown, "nomod.NothingNames", 1, 1);
        unknown.writeByte(2);
        ByteBufUtils.writeUTF8String(unknown, "x");
        // A number where a mob name goes
        writeRole(unknown, MobNames.class.getName(), 1, 1);
        unknown.writeByte(1);
        unknown.writeInt(7);
        ResolvedNames dropped = ResolvedNames.decode(unknown);
        helper.assertEquals(0, dropped.tooMany(), "dropped things aren't too much");
        helper.assertTrue(
            dropped.matches(MobNames.class)
                .isEmpty(),
            "a mob that isn't text should be dropped");
        helper.succeed();
    }

    // Any client can send one, so sizes in it can't be trusted. Too big is the same as matching too much, and counts
    // that overflow when added can't sneak past
    @GameTest
    public static void oversizedSentFiltersAreRefused(GameTestHelper helper) {
        EntityPlayerMP player = player(helper, "name_oversized");

        sendRaw(player, "big", 0, 5000, 0, 0);
        assertRefused(helper, player, "big", "5000 items");

        sendRaw(player, "negative", 0, -1, 0, 0);
        assertRefused(helper, player, "negative", "a negative count");

        sendRaw(player, "overflow", 0, 10, Integer.MAX_VALUE, 0);
        assertRefused(helper, player, "overflow", "counts that overflow");

        sendRaw(player, "achievements", 0, 10, 0, Integer.MAX_VALUE);
        assertRefused(helper, player, "achievements", "achievement counts that overflow");

        ByteBuf manyRoles = Unpooled.buffer();
        manyRoles.writeInt(0);
        manyRoles.writeInt(Integer.MAX_VALUE);
        answer(player, "many_roles", ResolvedNames.decode(manyRoles));
        assertRefused(helper, player, "many_roles", "a huge number of roles");

        sendRaw(player, "flagged", 4000, 0, 0, 0);
        assertRefused(helper, player, "flagged", "a client that said it matched too much");
        helper.succeed();
    }

    // MAX_MATCHES is the cap, and an exact meta next to any meta of the same ID doesn't count twice
    @GameTest
    public static void matchCapCountsDistinctThings(GameTestHelper helper) {
        EntityPlayerMP player = player(helper, "name_cap");

        ResolvedNames.Builder atCap = new ResolvedNames.Builder();
        for (int id = 0; id < ResolvedNames.MAX_MATCHES; id++) {
            atCap.item(id, ResolvedNames.ANY_META);
            atCap.item(id, 3);
        }
        answer(player, "at_cap", atCap.build());
        answered(player, "at_cap");

        ResolvedNames.Builder overCap = new ResolvedNames.Builder();
        for (int id = 0; id <= ResolvedNames.MAX_MATCHES; id++) overCap.item(id, 0);
        answer(player, "over_cap", overCap.build());
        assertRefused(helper, player, "over_cap", "one more than the cap");

        // Under the cap but too big for one packet
        ResolvedNames.Builder longNames = new ResolvedNames.Builder();
        for (int i = 0; i < ResolvedNames.MAX_MATCHES; i++) longNames.entity("SomeMod.AVeryLongEntityNameNumber" + i);
        answer(
            player,
            "long_names",
            longNames.build()
                .fitForPacket());
        assertRefused(helper, player, "long_names", "a filter too big for one packet");

        ResolvedNames.Builder mobs = new ResolvedNames.Builder();
        for (int i = 0; i <= ResolvedNames.MAX_MATCHES; i++) mobs.entity("Mob" + i);
        answer(player, "mobs_over_cap", mobs.build());
        assertRefused(helper, player, "mobs_over_cap", "one more mob than the cap");
        helper.succeed();
    }

    // The round trip a lookup with f: makes. The server asks the client, only an answer from that player for that
    // request counts, and it gets the same checks as any filter. No answer in time fails
    @GameTest(timeoutTicks = (int) (NameRequests.ANSWER_TIMEOUT_MILLIS / 50) + 200)
    public static void askAndAnswer(GameTestHelper helper) {
        EntityPlayerMP asker = player(helper, "name_ask");
        EntityPlayerMP someoneElse = player(helper, "name_ask_other");
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());
        int[] ids = new int[4];

        ResolvedNames.Builder pig = new ResolvedNames.Builder();
        pig.entity("Pig");
        ByteBuf tooBig = Unpooled.buffer();
        tooBig.writeInt(0);
        tooBig.writeInt(5000);

        helper.startSequence()
            .thenExecute("ask four times, answer three of them, plus answers that shouldn't count", () -> {
                ids[0] = ask(asker, "pig", outcomes);
                ids[1] = ask(asker, "nothing", outcomes);
                ids[2] = ask(asker, "too_much", outcomes);
                ids[3] = ask(asker, "silent", outcomes);

                NameRequests.answered(someoneElse, ids[0], pig.build());
                NameRequests.answered(asker, -12345, pig.build());
                NameRequests.answered(asker, ids[0], pig.build());
                NameRequests.answered(asker, ids[1], new ResolvedNames.Builder().build());
                NameRequests.answered(asker, ids[2], ResolvedNames.decode(tooBig));
            })
            .thenWaitUntil("the three answers are handled", 20, () -> {
                helper.assertEquals(3L, (long) outcomes.size(), "outcomes " + outcomes);
                helper.assertTrue(outcomes.contains("pig resolved"), "pig wasn't resolved: " + outcomes);
                // Text that names nothing can still match commands, so it gets searched
                helper
                    .assertTrue(outcomes.contains("nothing resolved"), "an empty answer wasn't searched: " + outcomes);
                helper.assertTrue(
                    outcomes.contains("too_much failed tempora.command.lookup.name_too_many"),
                    "an oversized answer didn't fail as too much: " + outcomes);
            })
            .thenExecute("answer pig again after it's done", () -> NameRequests.answered(asker, ids[0], pig.build()))
            .thenWaitUntil(
                "the unanswered one times out, the late answer does nothing",
                (int) (NameRequests.ANSWER_TIMEOUT_MILLIS / 50) + 100,
                () -> {
                    helper.assertTrue(
                        outcomes.contains("silent failed tempora.command.lookup.name_no_answer"),
                        "no answer didn't time out: " + outcomes);
                    helper.assertEquals(4L, (long) outcomes.size(), "outcomes " + outcomes);
                })
            .thenSucceed();
    }

    // Notes how the request for text ended in outcomes
    private static int ask(EntityPlayerMP asker, String text, List<String> outcomes) {
        return NameRequests.ask(
            asker,
            text,
            names -> outcomes.add(text + " resolved"),
            error -> outcomes.add(text + " failed " + error.getMessage()));
    }

    // How typed text and names get folded before matching
    @GameTest
    public static void foldingMatchesHoweverItIsTyped(GameTestHelper helper) {
        assertMatches(helper, "ＳＴＯＮＥ", "Stone", "full width letters");
        assertMatches(helper, "inek", "İnek", "Turkish dotted I typed as i");
        assertMatches(helper, "İNEK", "İnek", "Turkish dotted I typed in capitals");
        assertMatches(helper, "KIRMIZI", "Kırmızı Yün", "Turkish dotless i typed in capitals");
        assertMatches(helper, "kirmizi", "Kırmızı Yün", "Turkish dotless i typed without a Turkish keyboard");
        assertMatches(helper, "ﾌﾞﾀ", "ブタ", "half width katakana");
        helper.assertFalse(
            TextFold.matchesShownName(TextFold.terms("フタ"), TextFold.fold("ブタ")),
            "kana marks are part of the letter, フタ shouldn't match ブタ");
        assertMatches(helper, "λιθοσ", "ΛΙΘΟΣ", "Greek capital sigma typed small");
        assertMatches(helper, "λιθος", "Λιθος", "Greek final sigma");
        assertMatches(helper, "eisenerz", "Eisenerz", "German");
        assertMatches(helper, "straße", "Straße", "German sharp s");
        assertMatches(helper, "铁矿石", "铁矿石", "Chinese");
        assertMatches(helper, "鉄鉱石", "鉄鉱石", "Japanese");
        assertMatches(helper, "철_광석", "철 광석", "Korean with a space");
        assertMatches(helper, "железная_руда", "Железная руда", "Russian with a space");
        assertMatches(helper, "my_item", "my_item", "a name with a real _");
        assertMatches(helper, "café", "Café", "an accent written as a separate mark");
        assertMatches(helper, "blue_stone", "§9Blue §cStone", "colour codes in a name");
        helper.assertEquals(
            0L,
            (long) TextFold.terms(",,,")
                .size(),
            "terms in only commas");
        helper.assertEquals(Arrays.asList("stone", "dirt"), TextFold.terms(",,Stone,,DIRT,"), "terms between commas");
        helper.assertEquals(
            Collections.singletonList("wool"),
            TextFold.terms("_,__,wool"),
            "terms of only _ would search for a space");
        assertMatches(helper, "red__wool", "Red Wool", "a doubled _");
        assertMatches(helper, "my__item", "my__item", "a name with a real doubled _");
        helper.assertFalse(
            TextFold.matchesShownName(TextFold.terms("cafe"), TextFold.fold("Café")),
            "accents are part of the letter, cafe shouldn't match Café");
        helper.succeed();
    }

    // Block changes match on either side, what was there before or what's there after
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void blockChangesMatchBeforeOrAfter(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.blockChangeLogger;
        EntityPlayerMP player = player(helper, "name_change");
        TestPos local = at(1, 1, 3);
        TestPos pos = helper.absolute(local);
        int dim = helper.getWorld().provider.dimensionId;
        region(helper, "name_change", at(0, 0, 0), at(4, 4, 4), TemporaEvents.BLOCK_CHANGE, LogRule.ON);
        Probe<FoundEvents<?>> dirt = new Probe<>(helper, "blockChangesMatchBeforeOrAfter.dirt");
        Probe<FoundEvents<?>> stone = new Probe<>(helper, "blockChangesMatchBeforeOrAfter.stone");
        Probe<FoundEvents<?>> both = new Probe<>(helper, "blockChangesMatchBeforeOrAfter.both");

        helper.startSequence()
            .thenExecute("stone turns to glass, then glass to dirt", () -> {
                TemporaEvents.blockChangeLogger.queueEventInfo(change(helper, pos, Blocks.stone, Blocks.glass));
                TemporaEvents.blockChangeLogger.queueEventInfo(change(helper, pos, Blocks.glass, Blocks.dirt));
            })
            .thenWaitUntil(
                "dirt (after) finds one",
                WRITE_TICKS,
                () -> helper.assertEquals(
                    1L,
                    (long) events(
                        helper,
                        dirt.poll(() -> near(logger, dim, pos, sentItem(player, "dirt", Blocks.dirt)))).size(),
                    "changes found"))
            .thenWaitUntil(
                "stone (before) finds one",
                WRITE_TICKS,
                () -> helper.assertEquals(
                    1L,
                    (long) events(
                        helper,
                        stone.poll(() -> near(logger, dim, pos, sentItem(player, "stone", Blocks.stone)))).size(),
                    "changes found"))
            .thenWaitUntil(
                "glass (after one, before the other) finds both",
                WRITE_TICKS,
                () -> helper.assertEquals(
                    2L,
                    (long) events(
                        helper,
                        both.poll(() -> near(logger, dim, pos, sentItem(player, "glass", Blocks.glass)))).size(),
                    "changes found"))
            .thenSucceed();
    }

    // Items go by exact meta for items with subtypes, any meta for ones without
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void inventoryItemsByMeta(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.inventoryLogger;
        EntityPlayerMP player = player(helper, "name_inventory");
        TestPos pos = helper.absolute(at(3, 1, 1));
        int dim = helper.getWorld().provider.dimensionId;
        int dye = Item.getIdFromItem(Items.dye);
        Probe<FoundEvents<?>> exact = new Probe<>(helper, "inventoryItemsByMeta.exact");
        Probe<FoundEvents<?>> any = new Probe<>(helper, "inventoryItemsByMeta.any");

        ResolvedNames.Builder lapis = new ResolvedNames.Builder();
        lapis.item(dye, 4);
        answer(player, "lapis", lapis.build());

        helper.startSequence()
            .thenExecute("ink sac, lapis and bone meal go in a chest", () -> {
                TemporaEvents.inventoryLogger.queueEventInfo(moved(helper, pos, dye, 0));
                TemporaEvents.inventoryLogger.queueEventInfo(moved(helper, pos, dye, 4));
                TemporaEvents.inventoryLogger.queueEventInfo(moved(helper, pos, dye, 15));
            })
            .thenWaitUntil("the client's lapis (dye:4) finds just that", WRITE_TICKS, () -> {
                List<GenericEventInfo> found = events(
                    helper,
                    exact.poll(() -> near(logger, dim, pos, answered(player, "lapis"))));
                helper.assertEquals(1L, (long) found.size(), "moves found");
                helper.assertEquals(4L, (long) ((InventoryEventInfo) found.get(0)).itemMetadata, "meta found");
            })
            .thenWaitUntil(
                "any meta of dye finds all three",
                WRITE_TICKS,
                () -> helper.assertEquals(
                    3L,
                    (long) events(
                        helper,
                        any.poll(() -> near(logger, dim, pos, sentItem(player, "any_dye", Items.dye)))).size(),
                    "moves found"))
            .thenSucceed();
    }

    // Containers go by the name stored for them, matched on the server like commands
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void inventoryContainersByName(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.inventoryLogger;
        EntityPlayerMP player = player(helper, "name_containers");
        TestPos pos = helper.absolute(at(2, 1, 2));
        int dim = helper.getWorld().provider.dimensionId;
        int stone = Item.getIdFromItem(Item.getItemFromBlock(Blocks.stone));
        String[] texts = { "neutron_activator", "CHEST", "furnace" };
        long[] expected = { 1, 2, 0 };

        GameTestSequence sequence = helper.startSequence()
            .thenExecute("stone goes in a chest, a large chest and a neutron activator", () -> {
                for (String container : new String[] { "Chest", "Large Chest", "Neutron Activator" }) {
                    InventoryEventInfo event = moved(helper, pos, stone, 0);
                    event.containerName = container;
                    TemporaEvents.inventoryLogger.queueEventInfo(event);
                }
            });
        for (int i = 0; i < texts.length; i++) {
            String text = texts[i];
            long count = expected[i];
            Probe<FoundEvents<?>> probe = new Probe<>(helper, "inventoryContainersByName." + i);
            sequence = sequence.thenWaitUntil(
                "f:" + text + " finds " + count,
                WRITE_TICKS,
                () -> helper.assertEquals(
                    count,
                    (long) events(helper, probe.poll(() -> near(logger, dim, pos, sentText(player, text)))).size(),
                    "moves found for f:" + text));
        }
        sequence.thenSucceed();
    }

    // Mobs go by their EntityList name, what the client matched pig to (Pig and Zombie Pigman)
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void mobSpawnsByName(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.entitySpawnLogger;
        EntityPlayerMP player = player(helper, "name_mobs");
        TestPos pos = helper.absolute(at(3, 1, 3));
        int dim = helper.getWorld().provider.dimensionId;
        Probe<FoundEvents<?>> pig = new Probe<>(helper, "mobSpawnsByName.pig");
        Probe<FoundEvents<?>> blocks = new Probe<>(helper, "mobSpawnsByName.blocks");

        helper.startSequence()
            .thenExecute("a pig, a zombie and a zombie pigman spawn", () -> {
                TemporaEvents.entitySpawnLogger.queueEventInfo(spawn(helper, pos, "Pig"));
                TemporaEvents.entitySpawnLogger.queueEventInfo(spawn(helper, pos, "Zombie"));
                TemporaEvents.entitySpawnLogger.queueEventInfo(spawn(helper, pos, "PigZombie"));
            })
            .thenWaitUntil(
                "pig finds the pig and the pigman",
                WRITE_TICKS,
                () -> helper.assertEquals(
                    2L,
                    (long) events(
                        helper,
                        pig.poll(() -> near(logger, dim, pos, sentMobs(player, "pig", "Pig", "PigZombie")))).size(),
                    "spawns found"))
            .thenWaitUntil(
                "a block filter skips mob spawns",
                WRITE_TICKS,
                () -> helper.assertEquals(
                    0L,
                    (long) events(
                        helper,
                        blocks.poll(() -> near(logger, dim, pos, sentItem(player, "stone", Blocks.stone)))).size(),
                    "spawns found"))
            .thenSucceed();
    }

    // Commands match on their text as chat shows it, folded like names with _ for a space, on the server. The client
    // names nothing in any of these, it's all text
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void commandsByText(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.commandLogger;
        EntityPlayerMP player = player(helper, "name_commands");
        TestPos pos = helper.absolute(at(1, 1, 1));
        int dim = helper.getWorld().provider.dimensionId;
        String[] texts = { "tp", "TP_COLEN", "привет", "colen,ВСЕМ", "tp_всем", "tpx" };
        long[] expected = { 2, 1, 1, 2, 0, 1 };

        GameTestSequence sequence = helper.startSequence()
            .thenExecute("/tp Colen, /say Привет всем and /me waves f:tpx are run", () -> {
                TemporaEvents.commandLogger.queueEventInfo(command(helper, pos, "tp", "Colen"));
                TemporaEvents.commandLogger.queueEventInfo(command(helper, pos, "say", "Привет всем"));
                // Only lookups get left out for having f:<text> in them
                TemporaEvents.commandLogger.queueEventInfo(command(helper, pos, "me", "waves f:tpx"));
                // The lookup itself gets logged before it runs, f:tp mustn't find it
                TemporaEvents.commandLogger.queueEventInfo(command(helper, pos, "tempora", "lookup f:tp"));
            });
        for (int i = 0; i < texts.length; i++) {
            String text = texts[i];
            long count = expected[i];
            Probe<FoundEvents<?>> probe = new Probe<>(helper, "commandsByText." + i);
            sequence = sequence.thenWaitUntil(
                "f:" + text + " finds " + count,
                WRITE_TICKS,
                () -> helper.assertEquals(
                    count,
                    (long) events(helper, probe.poll(() -> near(logger, dim, pos, sentText(player, text)))).size(),
                    "commands found for f:" + text));
        }
        sequence.thenSucceed();
    }

    // Achievements go by stat ID, what the client matched the shown name to
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void achievementsByID(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.achievementLogger;
        EntityPlayerMP player = player(helper, "name_achievements");
        TestPos pos = helper.absolute(at(1, 1, 2));
        int dim = helper.getWorld().provider.dimensionId;
        Probe<FoundEvents<?>> wood = new Probe<>(helper, "achievementsByID.wood");
        Probe<FoundEvents<?>> blocks = new Probe<>(helper, "achievementsByID.blocks");

        ResolvedNames.Builder builder = new ResolvedNames.Builder();
        builder.achievement("achievement.mineWood");
        answer(player, "wood", builder.build());

        helper.startSequence()
            .thenExecute("Getting Wood and Taking Inventory are got", () -> {
                TemporaEvents.achievementLogger.queueEventInfo(achievement(helper, pos, "achievement.mineWood"));
                TemporaEvents.achievementLogger.queueEventInfo(achievement(helper, pos, "achievement.openInventory"));
            })
            .thenWaitUntil(
                "wood finds Getting Wood",
                WRITE_TICKS,
                () -> helper.assertEquals(
                    1L,
                    (long) events(helper, wood.poll(() -> near(logger, dim, pos, answered(player, "wood")))).size(),
                    "achievements found"))
            .thenWaitUntil(
                "a block filter skips achievements",
                WRITE_TICKS,
                () -> helper.assertEquals(
                    0L,
                    (long) events(
                        helper,
                        blocks.poll(() -> near(logger, dim, pos, sentItem(player, "stone", Blocks.stone)))).size(),
                    "achievements found"))
            .thenSucceed();
    }

    // Player deaths go by the mob that killed them, which killedBy holds as [Zombie]
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void playerDeathsByKiller(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.playerDeathLogger;
        EntityPlayerMP player = player(helper, "name_deaths");
        TestPos pos = helper.absolute(at(1, 1, 1));
        int dim = helper.getWorld().provider.dimensionId;
        Probe<FoundEvents<?>> zombie = new Probe<>(helper, "playerDeathsByKiller.zombie");

        answer(
            player,
            "zombie",
            new ResolvedNames.Builder().entity("Zombie")
                .build());

        helper.startSequence()
            .thenExecute("a player dies to a zombie and another to a fall", () -> {
                TemporaEvents.playerDeathLogger.queueEventInfo(playerDeath(helper, pos, "[Zombie]"));
                TemporaEvents.playerDeathLogger.queueEventInfo(playerDeath(helper, pos, "[fall]"));
            })
            .thenWaitUntil(
                "zombie finds the zombie death",
                WRITE_TICKS,
                () -> helper.assertEquals(
                    1L,
                    (long) events(helper, zombie.poll(() -> near(logger, dim, pos, answered(player, "zombie")))).size(),
                    "deaths found"))
            .thenSucceed();
    }

    // The most a filter can hold, on the logger that checks two pairs of columns, still runs and finds the one event
    @GameTest(timeoutTicks = TIMEOUT_TICKS)
    public static void fullSizeFilterStillRuns(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.blockChangeLogger;
        EntityPlayerMP player = player(helper, "name_full");
        TestPos pos = helper.absolute(at(2, 1, 4));
        int dim = helper.getWorld().provider.dimensionId;
        region(helper, "name_full", at(0, 0, 0), at(4, 4, 4), TemporaEvents.BLOCK_CHANGE, LogRule.ON);
        Probe<FoundEvents<?>> found = new Probe<>(helper, "fullSizeFilterStillRuns.found");

        // Half exact metas, half any meta, with sponge (id 19) as the last exact one
        ResolvedNames.Builder full = new ResolvedNames.Builder();
        int sponge = Block.getIdFromBlock(Blocks.sponge);
        for (int i = 0; i < ResolvedNames.MAX_MATCHES / 2 - 1; i++) full.item(10_000 + i, i % 16);
        for (int i = 0; i < ResolvedNames.MAX_MATCHES / 2; i++) full.item(20_000 + i, ResolvedNames.ANY_META);
        full.item(sponge, 0);
        answer(player, "full", full.build());

        helper.startSequence()
            .thenExecute(
                "air turns to sponge",
                () -> {
                    TemporaEvents.blockChangeLogger.queueEventInfo(change(helper, pos, Blocks.air, Blocks.sponge));
                })
            .thenWaitUntil("the full filter finds it", WRITE_TICKS, () -> {
                FoundEvents<?> result = found.poll(() -> near(logger, dim, pos, answered(player, "full")));
                helper.assertFalse(result.failed(), "the query failed");
                helper.assertEquals(1L, (long) events(helper, result).size(), "changes found");
            })
            .thenSucceed();
    }

    // Any meta of a block or item, as the client sends for one with no subtypes
    private static TextFilter sentItem(EntityPlayerMP player, String text, Object blockOrItem) {
        ResolvedNames.Builder builder = new ResolvedNames.Builder();
        int id = blockOrItem instanceof Block block ? Block.getIdFromBlock(block)
            : Item.getIdFromItem((Item) blockOrItem);
        builder.item(id, ResolvedNames.ANY_META);
        answer(player, text, builder.build());
        return answered(player, text);
    }

    // Text the client found no names for, so only text like commands can match it
    private static TextFilter sentText(EntityPlayerMP player, String text) {
        answer(player, text, new ResolvedNames.Builder().build());
        return answered(player, text);
    }

    private static TextFilter sentMobs(EntityPlayerMP player, String text, String... names) {
        sendEntities(player, text, names);
        return answered(player, text);
    }

    private static void assertMatches(GameTestHelper helper, String typed, String name, String what) {
        helper.assertTrue(
            TextFold.matchesShownName(TextFold.terms(typed), TextFold.fold(name)),
            what + ": " + typed + " didn't match " + name);
    }

    private static void assertRefused(GameTestHelper helper, EntityPlayerMP player, String text, String what) {
        helper.assertThrows(CommandException.class, () -> answered(player, text), what + " was accepted");
    }

    private static void sendEntities(EntityPlayerMP player, String text, String... names) {
        ResolvedNames.Builder builder = new ResolvedNames.Builder();
        for (String name : names) builder.entity(name);
        answer(player, text, builder.build());
    }

    // A packet as a client could write it, with whatever counts it likes and nothing after them
    private static void sendRaw(EntityPlayerMP player, String text, int tooMany, int itemCount, int entityCount,
        int achievementCount) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(tooMany);
        buf.writeInt(3);
        writeRole(buf, ItemNames.class.getName(), 2, itemCount);
        if (itemCount > 0 && itemCount <= ResolvedNames.MAX_MATCHES) {
            for (int i = 0; i < itemCount; i++) {
                buf.writeByte(1);
                buf.writeInt(i);
                buf.writeByte(0);
            }
        }
        writeRole(buf, MobNames.class.getName(), 1, entityCount);
        writeRole(buf, AchievementNames.class.getName(), 1, achievementCount);
        answer(player, text, ResolvedNames.decode(buf));
    }

    // Header of one role's things in a ResolvedNames packet, the values follow
    private static void writeRole(ByteBuf buf, String role, int parts, int count) {
        ByteBufUtils.writeUTF8String(buf, role);
        buf.writeInt(parts);
        buf.writeInt(count);
    }

    private static List<GenericEventInfo> events(GameTestHelper helper, FoundEvents<?> found) {
        helper.assertFalse(found.failed(), "the query failed");
        return FoundEvents.newestAcross(Collections.singletonList(found), 100);
    }

    private static FoundEvents<?> near(GenericPositionalLogger<?> logger, int dim, TestPos pos, TextFilter names) {
        return logger
            .findEventsNear(dim, pos.x(), pos.y(), pos.z(), 0, TimeWindow.allTime(), PlayerFilter.anyone(), names, 100);
    }

    private static <E extends PositionalEventInfo> E placed(GameTestHelper helper, TestPos pos, E event) {
        event.timestamp = System.currentTimeMillis() - 60_000;
        event.dimensionID = helper.getWorld().provider.dimensionId;
        event.x = pos.x();
        event.y = pos.y();
        event.z = pos.z();
        return event;
    }

    private static void breakAt(GameTestHelper helper, EntityPlayerMP player, TestPos pos, Block block, int meta) {
        PlayerBlockBreakEventInfo event = placed(helper, pos, new PlayerBlockBreakEventInfo());
        event.blockID = Block.getIdFromBlock(block);
        event.metadata = meta;
        event.pickBlockID = event.blockID;
        event.pickBlockMeta = meta;
        event.playerUUID = PositionalLoggerTests.uuid(player);
        event.encodedNBT = "";
        TemporaEvents.playerBlockBreakLogger.queueEventInfo(event);
    }

    private static EntityDeathEventInfo death(GameTestHelper helper, TestPos pos, String name) {
        EntityDeathEventInfo event = placed(helper, pos, new EntityDeathEventInfo());
        event.entityName = name;
        return event;
    }

    private static EntitySpawnEventInfo spawn(GameTestHelper helper, TestPos pos, String name) {
        EntitySpawnEventInfo event = placed(helper, pos, new EntitySpawnEventInfo());
        event.entityName = name;
        return event;
    }

    private static BlockChangeEventInfo change(GameTestHelper helper, TestPos pos, Block before, Block after) {
        BlockChangeEventInfo event = placed(helper, pos, new BlockChangeEventInfo());
        event.beforeBlockID = Block.getIdFromBlock(before);
        event.beforePickBlockID = event.beforeBlockID;
        event.afterBlockID = Block.getIdFromBlock(after);
        event.afterPickBlockID = event.afterBlockID;
        return event;
    }

    private static CommandEventInfo command(GameTestHelper helper, TestPos pos, String name, String arguments) {
        CommandEventInfo event = placed(helper, pos, new CommandEventInfo());
        event.commandName = name;
        event.arguments = arguments;
        return event;
    }

    private static AchievementEventInfo achievement(GameTestHelper helper, TestPos pos, String statID) {
        AchievementEventInfo event = placed(helper, pos, new AchievementEventInfo());
        event.achievementID = statID;
        return event;
    }

    private static PlayerDeathEventInfo playerDeath(GameTestHelper helper, TestPos pos, String killedBy) {
        PlayerDeathEventInfo event = placed(helper, pos, new PlayerDeathEventInfo());
        event.killedBy = killedBy;
        return event;
    }

    private static InventoryEventInfo moved(GameTestHelper helper, TestPos pos, int itemID, int meta) {
        InventoryEventInfo event = placed(helper, pos, new InventoryEventInfo());
        event.itemID = itemID;
        event.itemMetadata = meta;
        event.amount = 1;
        return event;
    }
}
