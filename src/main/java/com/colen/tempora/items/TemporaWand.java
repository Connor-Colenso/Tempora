package com.colen.tempora.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.PlayerUtils;

public class TemporaWand extends Item {

    public TemporaWand() {
        // Max stack size. Common values are 1 for tools/weapons, 16 for special items, and 64 for most other items.
        this.setMaxStackSize(1);

        // Set the creative tab for this item.
        this.setCreativeTab(CreativeTabs.tabTools); // Adjust this to whatever tab you want the item to appear in.

        // Set the unlocalized and registry name for this item.
        this.setUnlocalizedName("tempora_wand"); // This is used for localization.

        this.setTextureName("tempora:tempora_wand");
    }

    // @Override
    // public ItemStack onItemRightClick(ItemStack itemStackIn, World world, EntityPlayer player) {
    // if (!PlayerUtils.isPlayerOp(player)) {
    // PlayerUtils.sendMessageToOps("player.tempora.wand.unauthorised", player.getDisplayName());
    // } else {
    // if (TemporaUtils.isServerSide()) {
    // for (GenericPositionalLogger<?> logger : GenericPositionalLogger.getLoggerList()) {
    // GenericPositionalLogger.queryEventsAtPosAndTime(player, radius, entityPlayerMP.posX, entityPlayerMP.posY,
    // entityPlayerMP.posZ, seconds, tableName);
    //
    // }
    // }
    //
    // }
    // return super.onItemRightClick(itemStackIn, world, player);
    // }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (TemporaUtils.isClientSide()) return false;

        if (!PlayerUtils.isPlayerOp(player)) {
            PlayerUtils.sendMessageToOps("player.tempora.wand.unauthorised", player.getDisplayName());
        } else {
            for (GenericPositionalLogger<?> logger : GenericPositionalLogger.getLoggerList()) {
                GenericPositionalLogger
                    .queryEventsAtPosAndTime(player, x, y, z, -1, logger.getSQLTableName());
            }
        }

        return false;
    }
}
