package com.colen.tempora.gametests;

import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;

import com.colen.tempora.Tempora;
import com.colen.tempora.networking.handlers.TeleportRequestHandler;
import com.colen.tempora.networking.packets.PacketTeleportRequest;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.mojang.authlib.GameProfile;

/**
 * Clicking coordinates in chat sends the server a teleport request, which it carries out only for players who may use
 * /tempora. The click itself is client side, so these check the coordinates reach the client intact and the server's
 * checks, not the click.
 */
@GameTestHolder(Tempora.MODID)
public class TeleportTests {

    @GameTest
    public static void opIsTeleported(GameTestHelper helper) {
        TestPlayer player = testPlayer(helper, "tp_op", true);
        TestPos target = helper.absolute(at(3, 2, 3));

        TeleportRequestHandler.handle(
            player,
            new PacketTeleportRequest(target.x() + 0.5, target.y(), target.z() + 0.5, player.dimension));

        helper.assertEquals(target.x() + 0.5, player.posX, "x");
        helper.assertEquals((double) target.y(), player.posY, "y");
        helper.assertEquals(target.z() + 0.5, player.posZ, "z");
        helper.succeed();
    }

    // Any client can send the packet, so one from a player who can't use /tempora is refused.
    @GameTest
    public static void notOpIsRefused(GameTestHelper helper) {
        TestPlayer player = testPlayer(helper, "tp_not_op", false);
        double startX = player.posX;
        TestPos target = helper.absolute(at(3, 2, 3));

        TeleportRequestHandler
            .handle(player, new PacketTeleportRequest(target.x(), target.y(), target.z(), player.dimension));

        helper.assertEquals(startX, player.posX, "the player shouldn't have moved");
        assertOnlyError(helper, player, "tempora.teleport.no_permission");
        helper.succeed();
    }

    @GameTest
    public static void unknownDimensionIsRefused(GameTestHelper helper) {
        TestPlayer player = testPlayer(helper, "tp_bad_dim", true);
        double startX = player.posX;
        int missingDim = -987654;
        helper.assertTrue(
            MinecraftServer.getServer()
                .worldServerForDimension(missingDim) == null,
            "dimension " + missingDim + " shouldn't exist");

        TeleportRequestHandler.handle(player, new PacketTeleportRequest(0, 64, 0, missingDim));

        helper.assertEquals(startX, player.posX, "the player shouldn't have moved");
        assertOnlyError(helper, player, "tempora.dimension.invalid");
        helper.succeed();
    }

    // A NaN or infinite position, which only a modified client could send, is ignored.
    @GameTest
    public static void nonFinitePositionIsIgnored(GameTestHelper helper) {
        TestPlayer player = testPlayer(helper, "tp_nan", true);
        double startX = player.posX;
        double startY = player.posY;

        TeleportRequestHandler.handle(player, new PacketTeleportRequest(Double.NaN, 64, 0, player.dimension));
        TeleportRequestHandler
            .handle(player, new PacketTeleportRequest(0, Double.POSITIVE_INFINITY, 0, player.dimension));

        helper.assertEquals(startX, player.posX, "x");
        helper.assertEquals(startY, player.posY, "y");
        helper.succeed();
    }

    // ---------- Helpers ----------

    private static void assertOnlyError(GameTestHelper helper, TestPlayer player, String key) {
        helper.assertEquals(1L, player.messages.size(), "messages");
        IChatComponent message = player.messages.get(0);
        helper.assertTrue(
            message instanceof ChatComponentTranslation translation && translation.getKey()
                .equals(key),
            "expected " + key + ", got " + message);
        helper.assertEquals(
            EnumChatFormatting.RED,
            message.getChatStyle()
                .getColor(),
            "colour");
    }

    /**
     * A player with a net handler, as a teleport sends them their new position. Its connection goes nowhere, so what is
     * sent is queued and dropped. Never added to the world or the player list. Whether it may use /tempora is set
     * here when it should, as making a real op would rewrite ops.json; one that may not gets the server's own check.
     */
    private static TestPlayer testPlayer(GameTestHelper helper, String name, boolean mayUseTempora) {
        WorldServer world = helper.getWorld();
        String profileName = "tempora_" + name;
        GameProfile profile = new GameProfile(
            UUID.nameUUIDFromBytes(profileName.getBytes(StandardCharsets.UTF_8)),
            profileName);
        TestPlayer player = new TestPlayer(world, profile, mayUseTempora);
        new NetHandlerPlayServer(MinecraftServer.getServer(), new NetworkManager(false), player);
        TestPos standing = helper.absolute(at(0, 1, 0));
        player.setPosition(standing.x() + 0.5, standing.y(), standing.z() + 0.5);
        return player;
    }

    private static class TestPlayer extends EntityPlayerMP {

        final List<IChatComponent> messages = new ArrayList<>();
        private final boolean mayUseTempora;

        TestPlayer(WorldServer world, GameProfile profile, boolean mayUseTempora) {
            super(MinecraftServer.getServer(), world, profile, new ItemInWorldManager(world));
            this.mayUseTempora = mayUseTempora;
        }

        @Override
        public boolean canCommandSenderUseCommand(int level, String command) {
            return mayUseTempora || super.canCommandSenderUseCommand(level, command);
        }

        @Override
        public void addChatMessage(IChatComponent message) {
            messages.add(message);
        }
    }
}
