package com.colen.tempora.items;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;
import static com.colen.tempora.utils.GenericUtils.getDimensionName;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Facing;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.commands.LookupCommand;
import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.commands.command_base.SortOrder;
import com.colen.tempora.networking.PlayerPackets;
import com.colen.tempora.networking.packets.PacketShowRegionInWorld;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.rendering.ClientRegionStore;
import com.colen.tempora.rendering.regions.RegionRenderMode;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TextFilter;
import com.colen.tempora.utils.TimeWindow;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

// Left click a block to see its history, right click a face to see the history of the space in front of it (where a
// block placed on that face would go). Either way the checked block gets outlined
public class TemporaWand extends Item {

    // Holding a mouse button repeats the click every few ticks, server side too. Only a click on another block or after
    // a pause checks again, so holding it doesn't spam chat with the same lookup
    private static final long REPEAT_CLICK_MILLIS = 1000;
    private static final Map<EntityPlayer, LastCheck> lastChecks = new WeakHashMap<>();

    public TemporaWand() {
        this.setMaxStackSize(1);
        // No creative tab so creative players who can't use /tempora never pick it up, /tempora wand gives one
        this.setCreativeTab(null);
        this.setUnlocalizedName("tempora_wand");
        this.setTextureName("tempora:tempora_wand");
    }

    // Left click, on the Forge bus. In 1.7.10 only the server hears about it since the client fires no event for
    // hitting a block, so the server outlines the block for the client
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @SuppressWarnings("unused")
    public void onLeftClickBlock(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        if (!(event.entityPlayer instanceof EntityPlayerMP player)) return;
        // A machine holding a wand (block breaker, activator) can't read what it shows
        if (player instanceof FakePlayer) return;

        ItemStack held = player.getHeldItem();
        if (held == null || held.getItem() != this) return;

        // Hitting the block mustn't start breaking it. The server also resends the block in case the client already
        // broke it
        event.setCanceled(true);

        if (isRepeatClick(player, event.x, event.y, event.z)) return;

        PlayerPackets.sendTo(
            new PacketShowRegionInWorld.RegionMsg(outline(player.dimension, event.x, event.y, event.z)),
            player);
        checkSpot(player, event.x, event.y, event.z);
    }

    // Otherwise survival mining cracks the block on the client while the button's held
    @Override
    public float getDigSpeed(ItemStack stack, Block block, int metadata) {
        return 0;
    }

    // In case a block breaks anyway, e.g. instant break in creative
    @Override
    public boolean onBlockStartBreak(ItemStack stack, int x, int y, int z, EntityPlayer player) {
        return true;
    }

    // Right click. First so the block doesn't react, otherwise chests and machines open and levers and doors flip
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        // A machine holding a wand can't read what it shows, and would alert admins as an unauthorised user
        if (player instanceof FakePlayer) return false;

        int px = x + Facing.offsetsXForSide[side];
        int py = y + Facing.offsetsYForSide[side];
        int pz = z + Facing.offsetsZForSide[side];

        // Client outlines the space itself, server does the check. Returning true stops the client telling the server
        // about the click so it has to do that itself
        if (!(player instanceof EntityPlayerMP entityPlayerMP)) {
            ClientRegionStore.add(outline(player.dimension, px, py, pz));
            Minecraft.getMinecraft()
                .getNetHandler()
                .addToSendQueue(new C08PacketPlayerBlockPlacement(x, y, z, side, stack, hitX, hitY, hitZ));
            return true;
        }

        if (!isRepeatClick(entityPlayerMP, px, py, pz)) {
            checkSpot(entityPlayerMP, px, py, pz);
        }

        return true;
    }

    private static TemporaWorldRegion outline(int dimension, int x, int y, int z) {
        TemporaWorldRegion region = new TemporaWorldRegion(dimension, x, y, z, x + 1, y + 1, z + 1);
        region.setRegionUUID(
            UUID.randomUUID()
                .toString());
        region.setPlayerAuthorUUID("");
        region.setLabel("");
        region.setRenderMode(RegionRenderMode.TEMPORA_WAND);
        region.setRenderStartTimeMs(System.currentTimeMillis());
        return region;
    }

    private static boolean isRepeatClick(EntityPlayerMP player, int x, int y, int z) {
        long now = System.currentTimeMillis();
        LastCheck last = lastChecks.get(player);
        boolean repeat = last != null && last.isAt(player.dimension, x, y, z)
            && now - last.millis < REPEAT_CLICK_MILLIS;

        // A held button keeps pushing the time on, so it never checks again until let go
        lastChecks.put(player, new LastCheck(player.dimension, x, y, z, now));
        return repeat;
    }

    private static void checkSpot(EntityPlayerMP player, int x, int y, int z) {
        if (TemporaCommand.isDisabled(player)) return;

        // Same check as /tempora itself, so whoever can run lookups can use the wand and nobody else
        if (!CommandUtils.mayUseTempora(player)) {
            IChatComponent alert = new ChatComponentTranslation("tempora.wand.alert");
            alert.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            PlayerUtils.sendMessageToAuthorisedTemporaUsers(
                EventChat.line(
                    "tempora.wand.unauthorised",
                    alert,
                    EventChat.player(
                        player.getUniqueID()
                            .toString()),
                    teleportChatComponent(x, y, z, player.dimension, CommandUtils.TeleportType.BLOCK)));
            LOG.warn(
                "[TemporaWand] Unauthorised use attempt by player '{}' ({}) at ({}, {}, {}) in dimension {}.",
                player.getCommandSenderName(),
                player.getUniqueID(),
                x,
                y,
                z,
                player.dimension);
        } else {
            player.addChatMessage(
                new ChatComponentTranslation(
                    "tempora.wand.checking_pos",
                    teleportChatComponent(x, y, z, player.dimension, CommandUtils.TeleportType.BLOCK),
                    getDimensionName(player.dimension))
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));

            LookupCommand.showEvents(
                player,
                TemporaLoggerManager.getPositionalLoggers(),
                x,
                y,
                z,
                0,
                TimeWindow.allTime(),
                PlayerFilter.anyone(),
                TextFilter.any(),
                LookupCommand.DEFAULT_COUNT,
                SortOrder.LOGGER,
                null,
                new ChatComponentTranslation("tempora.wand.nothing_found"));
        }
    }

    private static final class LastCheck {

        final int dimension, x, y, z;
        final long millis;

        LastCheck(int dimension, int x, int y, int z, long millis) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.millis = millis;
        }

        boolean isAt(int dimension, int x, int y, int z) {
            return this.dimension == dimension && this.x == x && this.y == y && this.z == z;
        }
    }
}
