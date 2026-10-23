package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.TestSupport.assertWellFormed;
import static com.colen.tempora.gametests.TestSupport.eventsNear;
import static com.colen.tempora.gametests.TestSupport.region;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.block_change.BlockChangeEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.regions.LogRule;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/**
 * The block change logger sees both ways a chunk's blocks change: a block set (Chunk.func_150807_a) and a metadata
 * change alone (Chunk.setBlockMetadata), as doors, levers, crops growing and machines turning do.
 */
@GameTestHolder(Tempora.MODID)
public class BlockChangeTests {

    // setBlockMetadata is func_76589_b outside the dev environment, so this one only means something in the full
    // pack (runFullPackServerTests), where the mod runs reobfuscated. An unmapped hook once matched nothing there.
    @GameTest(timeoutTicks = 200)
    public static void metadataOnlyChangeIsStored(GameTestHelper helper) {
        GenericPositionalLogger<?> logger = TemporaEvents.blockChangeLogger;
        TestPos wool = at(1, 1, 1);
        Probe<List<PositionalEventInfo>> events = new Probe<>(helper, "metadataOnlyChangeIsStored");

        helper.startSequence()
            .thenExecute("recolour a wool block by its metadata alone", () -> {
                // Placed before the region exists, so the only change stored is the metadata one.
                helper.setBlock(wool, Blocks.wool, 0);
                region(helper, "test_metadata_only", wool, wool, TemporaEvents.BLOCK_CHANGE, LogRule.ON);
                TestPos at = helper.absolute(wool);
                helper.assertTrue(
                    helper.getWorld()
                        .setBlockMetadataWithNotify(at.x(), at.y(), at.z(), 5, 3),
                    "the metadata did not change");
            })
            .thenWaitUntil(
                "the metadata change is stored",
                100,
                () -> helper.assertFalse(
                    events.poll(() -> eventsNear(helper, logger, helper.absolute(wool), 0))
                        .isEmpty(),
                    "no block change stored for a metadata-only change"))
            .thenExecute("it is stored as wool 0 to wool 5", () -> {
                List<PositionalEventInfo> stored = events.latest();
                assertWellFormed(helper, logger, stored);
                helper.assertEquals(1, stored.size(), "block changes stored for one metadata change");

                BlockChangeEventInfo change = (BlockChangeEventInfo) stored.get(0);
                int woolID = Block.getIdFromBlock(Blocks.wool);
                helper.assertEquals(woolID, change.beforeBlockID, "block before");
                helper.assertEquals(0, change.beforeMetadata, "metadata before");
                helper.assertEquals(woolID, change.afterBlockID, "block after");
                helper.assertEquals(5, change.afterMetadata, "metadata after");
            })
            .thenSucceed();
    }
}
