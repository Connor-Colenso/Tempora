package com.colen.tempora.commands;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.Tempora;
import com.colen.tempora.commands.command_base.TemporaSubcommand;

/**
 * /tempora wand
 * Gives the player a Tempora wand, which shows a block's history when clicked.
 */
public class WandCommand extends TemporaSubcommand {

    public WandCommand() {
        super("wand");
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        EntityPlayerMP player = (EntityPlayerMP) sender;

        ItemStack wand = new ItemStack(Tempora.temporaWand);
        if (!player.inventory.addItemStackToInventory(wand)) {
            // Inventory full, so drop it at their feet instead.
            player.dropPlayerItemWithRandomChoice(wand, false);
        }
        player.inventoryContainer.detectAndSendChanges();

        IChatComponent message = new ChatComponentTranslation("tempora.command.wand.given");
        message.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(message);
    }

    @Override
    public String getExampleArgs() {
        return "";
    }
}
