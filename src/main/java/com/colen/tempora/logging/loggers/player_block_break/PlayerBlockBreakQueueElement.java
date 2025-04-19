package com.colen.tempora.logging.loggers.player_block_break;

import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiOpenEvent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerBlockBreakQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public String playerNameWhoBrokeBlock;

    @Override
    public String localiseText() {
        String localizedName = BlockUtils.getLocalizedName(blockID, metadata);
        String formattedTime = TimeUtils.formatTime(timestamp);

        return StatCollector.translateToLocalFormatted(
            "message.block_break",
            playerNameWhoBrokeBlock,
            localizedName,
            blockID,
            metadata,
            Math.round(x),
            Math.round(y),
            Math.round(z),
            formattedTime);
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui instanceof GuiInventory) {
            // Code to execute when the player's inventory is opened
        }
    }

}
