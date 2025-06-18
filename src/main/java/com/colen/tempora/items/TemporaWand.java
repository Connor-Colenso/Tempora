package com.colen.tempora.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.logging.commands.QueryEventsCommand;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;

public class TemporaWand extends Item {

    public TemporaWand() {
        // Max stack size. Common values are 1 for tools/weapons, 16 for special items, and 64 for most other items.
        this.setMaxStackSize(1);

        // Set the creative tab for this item.
        this.setCreativeTab(CreativeTabs.tabTools); // Adjust this to whatever tab you want the item to appear in.

        // Set the unlocalized and registry name for this item.
        this.setUnlocalizedName("admin_wand"); // This is used for localization.
    }

    @Override
    public ItemStack onItemRightClick(ItemStack itemStackIn, World world, EntityPlayer player) {

        if (TemporaUtils.isServerSide()) {
            for (GenericPositionalLogger<?> logger : GenericPositionalLogger.getLoggerList()) {
                QueryEventsCommand.queryDatabases(player, 10, 3600, logger.getSQLTableName());
            }
        }

        return super.onItemRightClick(itemStackIn, world, player);
    }

}
