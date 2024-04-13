package com.colen.tempora.PositionalEvents.Loggers.BlockBreak;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.util.StatCollector;

import com.colen.tempora.PositionalEvents.Loggers.Generic.GenericQueueElement;
import com.colen.tempora.Utils.BlockUtils;
import com.colen.tempora.Utils.TimeUtils;
import net.minecraftforge.client.event.GuiOpenEvent;

public class BlockBreakQueueElement extends GenericQueueElement {

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
