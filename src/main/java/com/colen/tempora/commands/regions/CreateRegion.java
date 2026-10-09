package com.colen.tempora.commands.regions;

import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.utils.CommandUtils.OP_ONLY;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;

import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.DimensionManager;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.commands.command_base.CommandArg;
import com.colen.tempora.commands.command_base.TemporaCommandBase;
import com.colen.tempora.networking.packets.PacketShowRegionInWorld;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.rendering.regions.RegionRenderMode;
import com.colen.tempora.utils.CommandUtils;

/**
 * Creates an axis‑aligned, integer‑bounded region and stores it in the region registry. A new region has no rules,
 * so it changes nothing until /tempora_region_rule gives it some.
 */
public class CreateRegion extends TemporaCommandBase {

    public CreateRegion() {
        super(
            new CommandArg("<label>", "tempora.command.create_region.help.arg0"),
            new CommandArg("<x1> <y1> <z1>", "tempora.command.create_region.help.arg1"),
            new CommandArg("<x2> <y2> <z2>", "tempora.command.create_region.help.arg2"),
            new CommandArg("[dim_ID]", "tempora.command.create_region.help.arg3"));
    }

    @Override
    public String getCommandName() {
        return "tempora_create_region";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return OP_ONLY;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        // label + 6 coords + optional dimID
        if (args.length != 7 && args.length != 8) {
            sender.addChatMessage(CommandUtils.wrongUsage(getCommandUsage(sender)));
            return;
        }

        final String label = args[0];
        if (TemporaRegionRegistry.getByLabel(label) != null) {
            IChatComponent msg = new ChatComponentTranslation("tempora.command.create_region.label_taken", label);
            msg.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(msg);
            return;
        }

        int[] coords = new int[6];
        for (int i = 0; i < 6; i++) {
            coords[i] = parseInt(sender, args[i + 1]);
        }

        int dimID = args.length == 8 ? parseInt(sender, args[7]) : sender.getEntityWorld().provider.dimensionId;
        if (!DimensionManager.isDimensionRegistered(dimID)) {
            IChatComponent msg = new ChatComponentTranslation("tempora.dimension.invalid", dimID);
            msg.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(msg);
            return;
        }

        // Both corners are inclusive, like /fill, so "0 0 0 4 4 4" covers a 5x5x5 cube. Internally the max bounds
        // are exclusive, hence the + 1.
        TemporaWorldRegion region = new TemporaWorldRegion(
            dimID,
            Math.min(coords[0], coords[3]),
            Math.min(coords[1], coords[4]),
            Math.min(coords[2], coords[5]),
            Math.max(coords[0], coords[3]) + 1,
            Math.max(coords[1], coords[4]) + 1,
            Math.max(coords[2], coords[5]) + 1);

        region.setLabel(label);
        region.setRegionUUID(
            UUID.randomUUID()
                .toString());
        region.setRenderMode(RegionRenderMode.REGION);
        region.setRegionOriginTimeMs(System.currentTimeMillis());

        if (sender instanceof EntityPlayerMP player) {
            region.setPlayerAuthorUUID(
                player.getUniqueID()
                    .toString());
        } else {
            region.setPlayerAuthorUUID(UNKNOWN_PLAYER_NAME);
        }

        TemporaRegionRegistry.add(region);

        // Feedback
        ChatComponentTranslation msg = new ChatComponentTranslation(
            "tempora.command.create_region.success",
            label,
            region.getMinCornerChatComponent(),
            region.getMaxCornerChatComponent(),
            region.getMidpointChatComponent());

        msg.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(msg);

        // The most common reason to make a region is to log block changes in it, so offer that.
        String blockLoggingCommand = "/" + SetRegionRule.COMMAND_NAME + " " + label + " " + TemporaEvents.BLOCK_CHANGE + " on";
        IChatComponent suggestion = new ChatComponentTranslation("tempora.command.create_region.suggest_block_logging");
        suggestion.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, blockLoggingCommand))
            .setChatHoverEvent(
                new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ChatComponentTranslation("tempora.command.create_region.suggest_block_logging.hover")));

        IChatComponent noRules = new ChatComponentTranslation("tempora.command.create_region.no_rules", suggestion);
        noRules.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        sender.addChatMessage(noRules);

        if (sender instanceof EntityPlayerMP player) {
            NETWORK.sendTo(new PacketShowRegionInWorld.RegionMsg(region), player);
        }
    }

    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.create_region.help.description");
    }

    @Override
    public String getExampleCommand() {
        return "/" + getCommandName() + " spawn -50 0 -50 50 255 50 0";
    }
}
