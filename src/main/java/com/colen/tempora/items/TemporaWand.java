package com.colen.tempora.items;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.loggers.generic.GenericQueueElement.generateTeleportChatComponent;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Facing;
import net.minecraft.world.World;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.TemporaUtils;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;

public class TemporaWand extends Item {

    public TemporaWand() {
        this.setMaxStackSize(1);
        this.setCreativeTab(CreativeTabs.tabTools);
        this.setUnlocalizedName("tempora_wand");
        this.setTextureName("tempora:tempora_wand");
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, int x, int y, int z, EntityPlayer player) {
        if (TemporaUtils.isServerSide()) {
            checkSpot(player, x, y, z);
        }

        // On the client, force a visual refresh so the block doesn't appear broken
        if (TemporaUtils.isServerSide()) {
            player.worldObj.markBlockForUpdate(x, y, z);
        }

        // Cancel the break on both sides
        return true;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (TemporaUtils.isClientSide()) return true;

        int px = x + Facing.offsetsXForSide[side];
        int py = y + Facing.offsetsYForSide[side];
        int pz = z + Facing.offsetsZForSide[side];

        checkSpot(player, px, py, pz);

        // Todo send client render packet to highlight selected coordinate.

        return true;
    }

    private static void checkSpot(EntityPlayer player, int x, int y, int z) {
        if (!PlayerUtils.isPlayerOp(player)) {
            PlayerUtils.sendMessageToOps("player.tempora.wand.unauthorised", player.getDisplayName());
            LOG.warn(
                "[TemporaWand] Unauthorised use attempt by player '{}' at ({}, {}, {}) in dimension {}.",
                player.getDisplayName(),
                x,
                y,
                z,
                player.dimension);
        } else {
            player.addChatMessage(
                new ChatComponentTranslation(
                    "msg.tempora.wand.checking.pos",
                    generateTeleportChatComponent(
                        x,
                        y,
                        z,
                        player.dimension,
                        player.getDisplayName(),
                        GenericQueueElement.CoordFormat.INT),
                    player.dimension).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));

            for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
                logger.getDatabaseManager()
                    .queryEventsAtPosAndTime(player, x, y, z, -1);
            }
        }
    }
}
