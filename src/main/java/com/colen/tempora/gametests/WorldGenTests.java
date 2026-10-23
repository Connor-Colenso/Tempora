package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.TestSupport.eventsNear;
import static com.colen.tempora.gametests.TestSupport.region;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.gen.ChunkProviderGenerate;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.ChunkDataEvent;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.block_change.BlockChangeEventInfo;
import com.colen.tempora.loggers.entity_spawn.EntitySpawnEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.regions.LogRule;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.utils.WorldGenPhaseTracker;
import com.colen.tempora.utils.WorldGenTicks;
import com.gtnewhorizons.horizonqa.api.GameTestArguments;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;

import cpw.mods.fml.common.Loader;

/**
 * World generation is not a block change anyone made, nor a mob spawning, so the block change and entity spawn
 * loggers leave it out in every dimension, whichever generator or mod places the blocks or mobs. What the block
 * updates it leaves behind do later is the world ticking and may be logged (gravel falling into a lava lake), except a
 * generated fluid or fire playing itself out (see WorldGenTicks). The generation tests turn both loggers on over new
 * chunks, generate them, then change a block and spawn a mob there by hand, which must be the only events stored up
 * to then.
 */
@GameTestHolder(Tempora.MODID)
public class WorldGenTests {

    private static final int NETHER = -1;

    // Loading a square of chunks populates all but its last row and column: a chunk is populated once the chunks
    // next to it on the +x and +z sides are there.
    private static final int AREA_CHUNKS = 4;

    // Rounds of pending block updates a test runs straight away, each round running all that are queued.
    private static final int UPDATE_ROUNDS = 40;

    // NewHorizonsCoreMod, which only the GTNH pack has.
    private static final String GTNH_CORE_MOD = "dreamcraft";

    @GameTest(timeoutTicks = 300)
    public static void netherGenerationIsNotLogged(GameTestHelper helper) {
        Area area = new Area(helper, "netherGenerationIsNotLogged", NETHER);

        helper.startSequence()
            .thenExecute("generate nether chunks, then change a block and spawn a mob there", () -> {
                area.generate();
                helper.assertTrue(area.populationChanges > 0, "population placed nothing, so there was nothing to log");
                area.changeByHand();
            })
            .thenWaitUntil("the hand's change and spawn are stored", 100, area::assertHandChangesStored)
            // Events are written in the order they were logged, so anything logged before them is stored by now.
            .thenWaitUntil("nothing from generation is stored", 100, area::assertOnlyHandChangesStored)
            .thenSucceed();
    }

    // Pre-generated chunks unload before the updates generation left run, so their marks are saved and read back.
    @GameTest(timeoutTicks = 300)
    public static void worldGenMarksSurviveReload(GameTestHelper helper) {
        Area area = new Area(helper, "worldGenMarksSurviveReload", NETHER);

        helper.startSequence()
            .thenExecute("generate nether chunks, then save and read back their updates", () -> {
                area.generate();
                // The nether's population leaves updates: fire ageing, lava still to flow.
                int marked = area.markedUpdates();
                helper.assertTrue(marked > 0, "generation left no world gen updates");
                area.saveAndReadBackUpdates();
                helper.assertEquals(marked, area.markedUpdates(), "world gen updates still marked once read back");
            })
            .thenSucceed();
    }

    // ---------- Updates world gen left, reaching blocks someone placed ----------

    // In a batch of their own, as they run every pending update of the test world.
    private static final String TICKS_BATCH = "tempora.worldgen_ticks";

    // Generated fire in a box of placed planks: the fire ageing and spreading into the air around it is world gen, the
    // planks burning are not.
    @GameTest(batch = TICKS_BATCH, timeoutTicks = 300)
    public static void generatedFireBurningPlacedPlanksIsLogged(GameTestHelper helper) {
        TestPos fire = at(2, 2, 2);
        TestPos[] planks = { at(1, 2, 2), at(3, 2, 2), at(2, 1, 2), at(2, 3, 2), at(2, 2, 1), at(2, 2, 3) };
        Cell cell = new Cell(helper, "generatedFireBurningPlacedPlanksIsLogged");

        helper.startSequence()
            .thenExecute("box generated fire in placed planks, run its updates, then set a block by hand", () -> {
                helper.assertTrue(
                    helper.getWorld()
                        .getGameRules()
                        .getGameRuleBooleanValue("doFireTick"),
                    "fire doesn't tick in the test world");
                for (TestPos pos : planks) helper.setBlock(pos, Blocks.planks);
                cell.generate(fire, Blocks.fire);
                cell.runPendingUpdates();

                boolean burned = false;
                for (TestPos pos : planks) burned |= cell.blockAt(pos) != Blocks.planks;
                helper.assertTrue(burned, "the fire burned none of the planks");
                cell.setBlockByHand();
            })
            .thenWaitUntil("the block set by hand is stored", 100, cell::assertHandChangeStored)
            .thenWaitUntil("the planks burning is stored, the fire spreading into air isn't", 100, () -> {
                boolean planksBurned = false;
                for (BlockChangeEventInfo event : cell.storedChanges()) {
                    Block before = Block.getBlockById(event.beforeBlockID);
                    Block after = Block.getBlockById(event.afterBlockID);
                    planksBurned |= before == Blocks.planks && after != Blocks.planks;
                    helper.assertFalse(
                        before == Blocks.air && after == Blocks.fire,
                        "generated fire spreading into air was stored, at " + Cell.where(event));
                }
                helper.assertTrue(planksBurned, "no planks burning was stored");
            })
            .thenSucceed();
    }

    // A generated spring in a walled basin with a placed torch next to it: the water flowing into air is world gen,
    // washing the torch away is not.
    @GameTest(batch = TICKS_BATCH, timeoutTicks = 300)
    public static void generatedSpringFlowingOverPlacedTorchIsLogged(GameTestHelper helper) {
        TestPos spring = at(2, 1, 2);
        TestPos torch = at(1, 1, 2);
        Cell cell = new Cell(helper, "generatedSpringFlowingOverPlacedTorchIsLogged");

        helper.startSequence()
            .thenExecute("put a generated spring by a placed torch, run its updates, then set a block by hand", () -> {
                for (int x = 0; x < 5; x++) {
                    for (int z = 0; z < 5; z++) {
                        helper.setBlock(at(x, 0, z), Blocks.stone);
                        if (x == 0 || x == 4 || z == 0 || z == 4) helper.setBlock(at(x, 1, z), Blocks.stone);
                    }
                }
                // Standing on the floor.
                helper.setBlock(torch, Blocks.torch, 5);
                cell.generate(spring, Blocks.flowing_water);
                cell.runPendingUpdates();

                helper.assertTrue(
                    cell.blockAt(torch)
                        .getMaterial() == Material.water,
                    "the spring didn't flow over the torch: " + cell.describeRow(1));
                cell.setBlockByHand();
            })
            .thenWaitUntil("the block set by hand is stored", 100, cell::assertHandChangeStored)
            .thenWaitUntil("the torch washing away is stored, the water flowing into air isn't", 100, () -> {
                TestPos torchAt = helper.absolute(torch);
                boolean torchWashedAway = false;
                for (BlockChangeEventInfo event : cell.storedChanges()) {
                    boolean atTorch = event.x == torchAt.x() && event.y == torchAt.y() && event.z == torchAt.z();
                    Block after = Block.getBlockById(event.afterBlockID);
                    if (atTorch) {
                        torchWashedAway |= Block.getBlockById(event.beforeBlockID) == Blocks.torch
                            && after.getMaterial() == Material.water;
                    } else {
                        helper.assertFalse(
                            after.getMaterial() == Material.water,
                            "generated water flowing into air was stored, at " + Cell.where(event));
                    }
                }
                helper.assertTrue(torchWashedAway, "the torch washing away was not stored");
            })
            .thenSucceed();
    }

    /** A test's own cell in the test world, with the block change logger on over it. */
    private static final class Cell {

        // The last change of each test, set by hand. Events are written in the order they were logged, so once it is
        // stored, everything before it is too.
        private static final TestPos HAND = at(4, 4, 4);

        private final GameTestHelper helper;
        private final GenericPositionalLogger<?> logger = TemporaEvents.blockChangeLogger;
        private final Probe<List<PositionalEventInfo>> handEvents;
        private final Probe<List<PositionalEventInfo>> cellEvents;

        Cell(GameTestHelper helper, String testName) {
            this.helper = helper;
            // Above the cell too, as fire spreads up to 4 blocks up.
            region(helper, testName, at(0, 0, 0), at(4, 8, 4), TemporaEvents.BLOCK_CHANGE, LogRule.ON);
            handEvents = new Probe<>(helper, testName + ".hand");
            cellEvents = new Probe<>(helper, testName + ".cell");

            // Hodgepodge puts a fluid's update off until the chunks within 6 blocks of it are loaded.
            TestPos min = helper.absolute(at(0, 0, 0));
            TestPos max = helper.absolute(at(4, 0, 4));
            ChunkProviderServer provider = helper.getWorld().theChunkProviderServer;
            for (int x = (Math.min(min.x(), max.x()) - 8) >> 4; x <= (Math.max(min.x(), max.x()) + 8) >> 4; x++) {
                for (int z = (Math.min(min.z(), max.z()) - 8) >> 4; z <= (Math.max(min.z(), max.z()) + 8) >> 4; z++) {
                    provider.loadChunk(x, z);
                }
            }
        }

        // Places a block as world generation does, so the updates it schedules on being placed are world gen.
        void generate(TestPos pos, Block block) {
            WorldGenPhaseTracker.enter();
            try {
                helper.setBlock(pos, block);
            } finally {
                WorldGenPhaseTracker.exit();
            }

            WorldServer world = helper.getWorld();
            TestPos at = helper.absolute(pos);
            boolean marked = false;
            List<NextTickListEntry> pending = world
                .getPendingBlockUpdates(world.getChunkFromBlockCoords(at.x(), at.z()), false);
            if (pending != null) {
                for (NextTickListEntry entry : pending) {
                    marked |= entry.xCoord == at.x() && entry.yCoord == at.y()
                        && entry.zCoord == at.z()
                        && WorldGenTicks.isWorldGen(entry, world);
                }
            }
            helper.assertTrue(marked, "the generated block left no world gen update");
        }

        // Runs the test world's pending updates straight away, rather than waiting for their time.
        void runPendingUpdates() {
            for (int i = 0; i < UPDATE_ROUNDS; i++) {
                helper.getWorld()
                    .tickUpdates(true);
            }
        }

        Block blockAt(TestPos pos) {
            TestPos at = helper.absolute(pos);
            return helper.getWorld()
                .getBlock(at.x(), at.y(), at.z());
        }

        // The blocks of one layer of the cell, z by z, for a failure message.
        String describeRow(int y) {
            StringBuilder row = new StringBuilder();
            for (int z = 0; z < 5; z++) {
                row.append("\n ");
                for (int x = 0; x < 5; x++) {
                    TestPos at = helper.absolute(at(x, y, z));
                    row.append(' ')
                        .append(Block.blockRegistry.getNameForObject(blockAt(at(x, y, z))))
                        .append(':')
                        .append(
                            helper.getWorld()
                                .getBlockMetadata(at.x(), at.y(), at.z()));
                }
            }
            return row.toString();
        }

        void setBlockByHand() {
            helper.setBlock(HAND, Blocks.gold_block);
        }

        void assertHandChangeStored() {
            helper.assertFalse(
                handEvents.poll(() -> eventsNear(helper, logger, helper.absolute(HAND), 0))
                    .isEmpty(),
                "the block set by hand was not stored");
        }

        // Every change stored in and around the cell.
        List<BlockChangeEventInfo> storedChanges() {
            List<BlockChangeEventInfo> changes = new ArrayList<>();
            for (PositionalEventInfo event : cellEvents
                .poll(() -> eventsNear(helper, logger, helper.absolute(at(2, 4, 2)), 4))) {
                changes.add((BlockChangeEventInfo) event);
            }
            return changes;
        }

        static String where(BlockChangeEventInfo event) {
            return (int) event.x + ", " + (int) event.y + ", " + (int) event.z;
        }
    }

    // ---------- Generation in the overworld and the dimensions of the GTNH pack ----------

    // Generates many areas, one or two a tick.
    private static final String WORLDGEN_BATCH = "tempora.worldgen";

    // Areas tried per dimension each run, each somewhere new.
    private static final int AREAS_PER_DIMENSION = 3;

    private static final int OVERWORLD = 0;

    // The overworld generated as a normal one would be, see asNormalOverworld: vanilla's terrain, biomes and
    // features, and every mod's overworld generation.
    @GameTest(batch = WORLDGEN_BATCH, timeoutTicks = 300)
    @MethodSource("overworldAreas")
    public static void overworldGenerationIsNotLogged(GameTestHelper helper, Integer index) {
        Area area = new Area(helper, "overworldGenerationIsNotLogged." + index, OVERWORLD).asNormalOverworld();
        generationIsNotLogged(helper, area, index);
    }

    public static List<GameTestArguments> overworldAreas() {
        List<GameTestArguments> rows = new ArrayList<>();
        for (int i = 0; i < AREAS_PER_DIMENSION; i++) rows.add(GameTestArguments.named("area_" + i, i));
        return rows;
    }

    // A structure: the start of the stronghold nearest the origin, which population builds from many pieces.
    @GameTest(batch = WORLDGEN_BATCH, timeoutTicks = 300)
    public static void overworldStrongholdIsNotLogged(GameTestHelper helper) {
        WorldServer world = MinecraftServer.getServer()
            .worldServerForDimension(OVERWORLD);
        ChunkPosition start = asNormalOverworld(world, () -> world.findClosestStructure("Stronghold", 0, 64, 0));
        helper.assertTrue(start != null, "the overworld has no strongholds");
        int startChunkX = start.chunkPosX >> 4, startChunkZ = start.chunkPosZ >> 4;
        helper.assumeTrue(
            !world.theChunkProviderServer.chunkExists(startChunkX, startChunkZ)
                && !((AnvilChunkLoader) world.theChunkProviderServer.currentChunkLoader)
                    .chunkExists(world, startChunkX, startChunkZ),
            "the stronghold was generated in this world before");
        // Its start chunk is in the middle of the populated ones.
        Area area = new Area(helper, "overworldStrongholdIsNotLogged", OVERWORLD, startChunkX - 1, startChunkZ - 1)
            .asNormalOverworld();

        helper.startSequence()
            .thenExecute("generate the stronghold's chunks, then change a block and spawn a mob there", () -> {
                area.generate();
                helper.assertTrue(area.populatedBlocks(Blocks.stonebrick) > 0, "no stronghold was built");
                area.changeByHand();
            })
            .thenWaitUntil("the hand's change and spawn are stored", 100, area::assertHandChangesStored)
            .thenWaitUntil("nothing from generation is stored", 100, area::assertOnlyHandChangesStored)
            .thenSucceed();
    }

    /**
     * Each dimension of the GTNH pack, by the name the pack gives it: their generators and the mods' world gen in them
     * are what the overworld and nether can't show. Skipped outside the full pack (runFullPackServerTests); in the
     * pack, a dimension missing from it fails, so this list follows the pack. One whose chunks came out empty (a gas
     * giant) is skipped too, as there was nothing to leave out.
     */
    @GameTest(batch = WORLDGEN_BATCH, timeoutTicks = 600)
    @MethodSource("gtnhDimensions")
    public static void gtnhGenerationIsNotLogged(GameTestHelper helper, String dimensionName, Integer index) {
        helper.assumeTrue(Loader.isModLoaded(GTNH_CORE_MOD), "needs the full GTNH pack");
        Integer dimension = dimensionsByName().get(dimensionName);
        helper.assertTrue(dimension != null, "the pack has no dimension named " + dimensionName);
        generationIsNotLogged(helper, new Area(helper, "gtnhGenerationIsNotLogged." + index, dimension), index);
    }

    // Generates the area, then changes a block and spawns a mob there by hand, which must be all that is stored.
    private static void generationIsNotLogged(GameTestHelper helper, Area area, int index) {
        helper.startSequence()
            // One area a tick, rather than all of them in the first.
            .thenIdle(1 + index)
            .thenExecute("generate chunks, then change a block and spawn a mob there", () -> {
                area.generate();
                helper.assumeTrue(area.terrainBlocks + area.populationChanges > 0, "the chunks tried are empty");
                area.changeByHand();
            })
            .thenWaitUntil("the hand's change and spawn are stored", 100, area::assertHandChangesStored)
            .thenWaitUntil("nothing from generation is stored", 100, area::assertOnlyHandChangesStored)
            .thenSucceed();
    }

    // The dimensions a full pack server lists, except the overworld and the nether (tested above), the End (void away
    // from its island) and personal worlds (made per player).
    private static final String[] GTNH_DIMENSIONS = { "Twilight Forest", "SpectreWorld", "Moon", "Mars", "Asteroids",
        "Makemake", "α Centauri Bb", "Barnarda C", "Barnarda E", "Barnarda F", "Kuiper Belt", "Europa", "Io", "Mercury",
        "Phobos", "Venus", "Deimos", "Enceladus", "Ceres", "Ganymede", "Titan", "Callisto", "Oberon", "Proteus",
        "Triton", "Pluto", "Uranus", "Saturn", "Neptune", "Jupiter", "Haumea", "Vega B", "T Ceti E", "Miranda", "Neper",
        "Maahes", "Anubis", "Horus", "Seth", "Mehen Belt", "Ross128b", "Ross128ba", "The Outer Lands", "Outer Lands",
        "Spirit World", "Torment", "Mirror", "Pocket Plane", "Bedrock", "Underdark", "The Last Millenium",
        "dimensionDarkWorld" };

    public static List<GameTestArguments> gtnhDimensions() {
        List<GameTestArguments> rows = new ArrayList<>();
        for (String name : GTNH_DIMENSIONS) {
            String key = name.replace("α", "alpha")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_");
            for (int area = 0; area < AREAS_PER_DIMENSION; area++) {
                rows.add(GameTestArguments.named(key + "_" + area, name, rows.size()));
            }
        }
        return rows;
    }

    /**
     * Runs {@code generation} with the overworld generating as a normal one would: the default world type, its biomes
     * and its generator, in place of Horizon-QA's void ones. Mods' generators see the overworld as in any world.
     */
    private static <T> T asNormalOverworld(WorldServer world, Supplier<T> generation) {
        WorldInfo info = world.getWorldInfo();
        WorldType worldType = info.getTerrainType();
        WorldChunkManager biomes = world.provider.worldChunkMgr;
        ChunkProviderServer chunks = world.theChunkProviderServer;
        IChunkProvider generator = chunks.currentChunkProvider;

        info.setTerrainType(WorldType.DEFAULT);
        world.provider.worldChunkMgr = new WorldChunkManager(world);
        chunks.currentChunkProvider = new ChunkProviderGenerate(world, world.getSeed(), true);
        try {
            return generation.get();
        } finally {
            chunks.currentChunkProvider = generator;
            world.provider.worldChunkMgr = biomes;
            info.setTerrainType(worldType);
        }
    }

    private static Map<String, Integer> dimensionsByName;

    // Every registered dimension by its name. Server thread only.
    private static Map<String, Integer> dimensionsByName() {
        if (dimensionsByName != null) return dimensionsByName;

        Map<String, Integer> byName = new HashMap<>();
        for (int id : DimensionManager.getStaticDimensionIDs()) {
            try {
                byName.put(
                    DimensionManager.createProviderFor(id)
                        .getDimensionName(),
                    id);
            } catch (Exception e) {
                Tempora.LOG.warn("Could not read the name of dimension {}", id, e);
            }
        }
        return dimensionsByName = byName;
    }

    /** A square of new chunks in one dimension, with the block change and entity spawn loggers on over it. */
    private static final class Area {

        private final GameTestHelper helper;
        private final GenericPositionalLogger<?> blockLogger = TemporaEvents.blockChangeLogger;
        private final GenericPositionalLogger<?> spawnLogger = TemporaEvents.entitySpawnLogger;
        private final int dimension;
        private final int chunkX, chunkZ;
        private final int handX, handY, handZ;
        private final Probe<List<PositionalEventInfo>> handEvents;
        private final Probe<List<PositionalEventInfo>> areaEvents;
        private final Probe<List<PositionalEventInfo>> handSpawnEvents;
        private final Probe<List<PositionalEventInfo>> areaSpawnEvents;
        private boolean normalOverworld;

        // Blocks the dimension's generator made for the chunks to be populated, other than air.
        int terrainBlocks;

        // Blocks population and its spill from the next chunks changed in the populated chunks, against the terrain
        // the dimension's generator made for them.
        int populationChanges;

        // Far out and random, so the chunks are new even in a world the tests ran in before.
        Area(GameTestHelper helper, String testName, int dimension) {
            this(
                helper,
                testName,
                dimension,
                20_000 + new Random().nextInt(100_000),
                20_000 + new Random().nextInt(100_000));
        }

        Area(GameTestHelper helper, String testName, int dimension, int chunkX, int chunkZ) {
            this.helper = helper;
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            // High up, e.g. above the nether's bedrock roof. It's stored as a change whatever was there.
            handX = (chunkX << 4) + AREA_CHUNKS * 8;
            handY = 200;
            handZ = (chunkZ << 4) + AREA_CHUNKS * 8;
            handEvents = new Probe<>(helper, testName + ".hand");
            areaEvents = new Probe<>(helper, testName + ".area");
            handSpawnEvents = new Probe<>(helper, testName + ".handSpawn");
            areaSpawnEvents = new Probe<>(helper, testName + ".areaSpawn");
        }

        // Generates the overworld as a normal one, see WorldGenTests.asNormalOverworld.
        Area asNormalOverworld() {
            normalOverworld = true;
            return this;
        }

        WorldServer world() {
            return MinecraftServer.getServer()
                .worldServerForDimension(dimension);
        }

        void generate() {
            WorldServer world = world();
            if (normalOverworld) {
                WorldGenTests.asNormalOverworld(world, () -> {
                    generate(world);
                    return null;
                });
            } else {
                generate(world);
            }
        }

        private void generate(WorldServer world) {
            int minX = chunkX << 4, minZ = chunkZ << 4, size = AREA_CHUNKS << 4;
            // Population reaches up to 8 blocks into the next chunks, so the region does too.
            TemporaWorldRegion region = region(
                helper,
                "test_worldgen_" + dimension + "_" + chunkX + "_" + chunkZ,
                dimension,
                minX - 16,
                0,
                minZ - 16,
                minX + size + 16,
                256,
                minZ + size + 16,
                TemporaEvents.BLOCK_CHANGE,
                LogRule.ON);
            TemporaRegionRegistry.setRule(region, TemporaEvents.ENTITY_SPAWN, LogRule.ON);

            ChunkProviderServer provider = world.theChunkProviderServer;
            for (int x = chunkX; x < chunkX + AREA_CHUNKS; x++) {
                for (int z = chunkZ; z < chunkZ + AREA_CHUNKS; z++) {
                    helper.assertFalse(
                        provider.chunkExists(x, z)
                            || ((AnvilChunkLoader) provider.currentChunkLoader).chunkExists(world, x, z),
                        "chunk " + x + ", " + z + " was generated before the test");
                }
            }

            // The terrain of the chunks to be populated, before population, straight from the generator. A copy:
            // the chunks the world gets are made again when they load.
            Map<Long, int[]> terrain = new HashMap<>();
            WorldGenPhaseTracker.enter();
            try {
                for (int x = chunkX; x < chunkX + AREA_CHUNKS - 1; x++) {
                    for (int z = chunkZ; z < chunkZ + AREA_CHUNKS - 1; z++) {
                        int[] blocks = blocks(provider.currentChunkProvider.provideChunk(x, z));
                        terrain.put(key(x, z), blocks);
                        for (int block : blocks) if (block >> 4 != 0) terrainBlocks++;
                    }
                }
            } finally {
                WorldGenPhaseTracker.exit();
            }

            for (int x = chunkX; x < chunkX + AREA_CHUNKS; x++) {
                for (int z = chunkZ; z < chunkZ + AREA_CHUNKS; z++) {
                    provider.loadChunk(x, z);
                }
            }

            for (int x = chunkX; x < chunkX + AREA_CHUNKS - 1; x++) {
                for (int z = chunkZ; z < chunkZ + AREA_CHUNKS - 1; z++) {
                    Chunk chunk = provider.provideChunk(x, z);
                    helper.assertTrue(chunk.isTerrainPopulated, "chunk " + x + ", " + z + " was not populated");
                    populationChanges += differences(terrain.get(key(x, z)), blocks(chunk));
                }
            }
        }

        /**
         * Saves the chunks, drops their pending updates from memory, then reads the updates back from the save, as a
         * chunk unloaded and loaded again would. The chunks themselves stay loaded: Hodgepodge keeps chunks next to
         * unpopulated ones from unloading for a while, so this goes through the chunk loader as Forge's chunk IO does.
         */
        void saveAndReadBackUpdates() {
            WorldServer world = world();
            AnvilChunkLoader loader = (AnvilChunkLoader) world.theChunkProviderServer.currentChunkLoader;
            try {
                for (int x = chunkX; x < chunkX + AREA_CHUNKS; x++) {
                    for (int z = chunkZ; z < chunkZ + AREA_CHUNKS; z++) {
                        Chunk chunk = world.getChunkFromChunkCoords(x, z);
                        loader.saveChunk(world, chunk);
                        world.getPendingBlockUpdates(chunk, true);
                    }
                }
                helper.assertEquals(0, pendingUpdates(), "pending block updates left after dropping them");

                for (int x = chunkX; x < chunkX + AREA_CHUNKS; x++) {
                    for (int z = chunkZ; z < chunkZ + AREA_CHUNKS; z++) {
                        Object[] saved = loader.loadChunk__Async(world, x, z);
                        helper.assertTrue(saved != null, "chunk " + x + ", " + z + " was not saved");
                        // As Forge's chunk IO loads a chunk. Only Tempora's handler hears the load event, as the
                        // chunk read back is a copy of one that stays loaded, which other mods needn't see.
                        Chunk copy = (Chunk) saved[0];
                        NBTTagCompound data = (NBTTagCompound) saved[1];
                        loader.loadEntities(world, data.getCompoundTag("Level"), copy);
                        new WorldGenTicks.ChunkSaves().onChunkLoad(new ChunkDataEvent.Load(copy, data));
                    }
                }
            } catch (IOException | MinecraftException e) {
                throw new RuntimeException(e);
            }
            helper.assertTrue(pendingUpdates() > 0, "no block updates were read back from the save");
        }

        // In the same tick as generation, so what is logged before them is generation's, and after them the world
        // ticking on.
        void changeByHand() {
            WorldServer world = world();
            world.setBlock(handX, handY, handZ, Blocks.gold_block);
            EntityPig pig = new EntityPig(world);
            pig.setPosition(handX + 0.5, handY + 1, handZ + 0.5);
            world.spawnEntityInWorld(pig);
        }

        void assertHandChangesStored() {
            helper.assertFalse(
                handEvents.poll(() -> eventsNear(blockLogger, dimension, handX, handY, handZ, 0))
                    .isEmpty(),
                "the block set by hand was not stored");
            helper.assertFalse(
                handSpawnEvents.poll(() -> eventsNear(spawnLogger, dimension, handX + 0.5, handY + 1, handZ + 0.5, 0))
                    .isEmpty(),
                "the mob spawned by hand was not stored");
        }

        // The hand's change and spawn are the only events stored up to them, see changeByHand.
        void assertOnlyHandChangesStored() {
            assertOnlyHandStored(blockLogger, handEvents, areaEvents, "block changes");
            assertOnlyHandStored(spawnLogger, handSpawnEvents, areaSpawnEvents, "entity spawns");
        }

        private void assertOnlyHandStored(GenericPositionalLogger<?> logger, Probe<List<PositionalEventInfo>> hand,
            Probe<List<PositionalEventInfo>> area, String what) {
            long handEventID = hand.latest()
                .get(0).eventID;
            List<PositionalEventInfo> events = new ArrayList<>();
            // Covers the whole height and more than the region on every side.
            for (PositionalEventInfo event : area.poll(() -> eventsNear(logger, dimension, handX, 128, handZ, 128))) {
                if (event.eventID <= handEventID) events.add(event);
            }
            // The hand's own, which is in the area too.
            if (events.size() == 1) return;

            StringBuilder found = new StringBuilder();
            for (int i = 0; i < Math.min(events.size(), 5); i++) {
                PositionalEventInfo event = events.get(i);
                String which = event instanceof BlockChangeEventInfo change ? Block.getBlockById(change.afterBlockID)
                    .getUnlocalizedName() + ":"
                    + change.afterMetadata : ((EntitySpawnEventInfo) event).entityName;
                found.append("\n  ")
                    .append(which)
                    .append(" at ")
                    .append((int) event.x)
                    .append(", ")
                    .append((int) event.y)
                    .append(", ")
                    .append((int) event.z);
            }
            helper.fail(events.size() + " " + what + " stored up to the hand's, expected only it:" + found);
        }

        // How many of a block the populated chunks hold.
        int populatedBlocks(Block block) {
            WorldServer world = world();
            int count = 0;
            for (int x = chunkX; x < chunkX + AREA_CHUNKS - 1; x++) {
                for (int z = chunkZ; z < chunkZ + AREA_CHUNKS - 1; z++) {
                    Chunk chunk = world.getChunkFromChunkCoords(x, z);
                    for (int i = 0; i < 16 * 16 * 256; i++) {
                        if (chunk.getBlock(i >> 12, i & 255, (i >> 8) & 15) == block) count++;
                    }
                }
            }
            return count;
        }

        // The area's pending updates that count as world gen.
        int markedUpdates() {
            WorldServer world = world();
            int count = 0;
            for (NextTickListEntry entry : pendingUpdateList()) {
                if (WorldGenTicks.isWorldGen(entry, world)) count++;
            }
            return count;
        }

        private int pendingUpdates() {
            return pendingUpdateList().size();
        }

        private List<NextTickListEntry> pendingUpdateList() {
            WorldServer world = world();
            List<NextTickListEntry> all = new ArrayList<>();
            for (int x = chunkX; x < chunkX + AREA_CHUNKS; x++) {
                for (int z = chunkZ; z < chunkZ + AREA_CHUNKS; z++) {
                    List<NextTickListEntry> updates = world
                        .getPendingBlockUpdates(world.getChunkFromChunkCoords(x, z), false);
                    if (updates != null) all.addAll(updates);
                }
            }
            return all;
        }
    }

    // Every block of a chunk as id and metadata.
    private static int[] blocks(Chunk chunk) {
        int[] blocks = new int[16 * 16 * 256];
        int i = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 256; y++) {
                    blocks[i++] = Block.getIdFromBlock(chunk.getBlock(x, y, z)) << 4 | chunk.getBlockMetadata(x, y, z);
                }
            }
        }
        return blocks;
    }

    private static int differences(int[] a, int[] b) {
        int count = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) count++;
        }
        return count;
    }

    private static long key(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
    }
}
