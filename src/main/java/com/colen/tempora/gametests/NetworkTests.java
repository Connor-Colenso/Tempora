package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.TestSupport.player;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.colen.tempora.Tempora;
import com.colen.tempora.networking.PlayerPackets;
import com.colen.tempora.networking.packets.PacketShowRegionInWorld;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.rendering.regions.RegionRenderMode;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;

/**
 * A packet FML can't send, to a player with no connection such as a machine's fake player, doesn't throw where it is
 * sent: FML keeps the error in Tempora's shared server channel and throws it at the next packet any player's client
 * sends Tempora, disconnecting that player. So nothing may send to a player without a connection.
 */
@GameTestHolder(Tempora.MODID)
public class NetworkTests {

    // A block breaker or activator holding a wand.
    @GameTest
    public static void wandUsedByMachineLeavesNetworkWorking(GameTestHelper helper) {
        String name = "tempora_wand_machine";
        FakePlayer machine = FakePlayerFactory.get(
            helper.getWorld(),
            new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        machine.inventory.currentItem = 0;
        machine.inventory.setInventorySlotContents(0, new ItemStack(Tempora.temporaWand));
        helper.afterTest(() -> machine.inventory.setInventorySlotContents(0, null));

        TestPos at = helper.absolute(TestPos.at(1, 1, 1));
        ForgeEventFactory.onPlayerInteract(
            machine,
            PlayerInteractEvent.Action.LEFT_CLICK_BLOCK,
            at.x(),
            at.y(),
            at.z(),
            1,
            helper.getWorld());

        assertNetworkWorking(helper);
        helper.succeed();
    }

    @GameTest
    public static void packetToPlayerWithoutConnectionLeavesNetworkWorking(GameTestHelper helper) {
        TestPos at = helper.absolute(TestPos.at(1, 1, 1));
        TemporaWorldRegion region = new TemporaWorldRegion(
            helper.getWorld().provider.dimensionId,
            at.x(),
            at.y(),
            at.z(),
            at.x() + 1,
            at.y() + 1,
            at.z() + 1);
        region.setRegionUUID(
            UUID.randomUUID()
                .toString());
        region.setPlayerAuthorUUID("");
        region.setLabel("");
        region.setRenderMode(RegionRenderMode.TEMPORA_WAND);
        region.setRenderStartTimeMs(System.currentTimeMillis());

        PlayerPackets
            .sendTo(new PacketShowRegionInWorld.RegionMsg(region), player(helper, "packetToPlayerWithoutConnection"));

        assertNetworkWorking(helper);
        helper.succeed();
    }

    // Throws, and clears, whatever error the channel is keeping for the next inbound packet.
    private static void assertNetworkWorking(GameTestHelper helper) {
        try {
            NetworkRegistry.INSTANCE.getChannel(Tempora.MODID, Side.SERVER)
                .checkException();
        } catch (Exception e) {
            helper.fail("Tempora's server channel is keeping an error for the next packet a player sends: " + e);
        }
    }
}
