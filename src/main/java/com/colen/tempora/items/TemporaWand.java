package com.colen.tempora.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Facing;
import net.minecraft.world.World;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
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
        return true;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (TemporaUtils.isClientSide()) return false;

        if (!PlayerUtils.isPlayerOp(player)) {
            PlayerUtils.sendMessageToOps("player.tempora.wand.unauthorised", player.getDisplayName());
        } else {
            for (GenericPositionalLogger<?> logger : GenericPositionalLogger.getLoggerList()) {
                int px = x + Facing.offsetsXForSide[side];
                int py = y + Facing.offsetsYForSide[side];
                int pz = z + Facing.offsetsZForSide[side];

                GenericPositionalLogger.queryEventsAtPosAndTime(player, px, py, pz, -1, logger.getSQLTableName());
            }
        }

        return false;
    }
}
