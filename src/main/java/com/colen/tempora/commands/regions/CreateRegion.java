package com.colen.tempora.commands.regions;

import static com.colen.tempora.utils.CommandUtils.OP_ONLY;
import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;

import java.util.UUID;

import com.colen.tempora.TemporaEvents;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.commands.command_base.CommandArg;
import com.colen.tempora.commands.command_base.TemporaCommandBase;
import com.colen.tempora.loggers.block_change.region_registry.TemporaRegionRegistry;
import com.colen.tempora.loggers.block_change.region_registry.TemporaWorldRegion;
import com.colen.tempora.rendering.regions.RegionRenderMode;
import com.colen.tempora.utils.CommandUtils;

/**
 * Creates an axis‑aligned, integer‑bounded region in the sender’s current dimension
 * and stores it in RegionRegistry.
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

        // Parse xyz start & xyz end
        int[] coords = new int[6];
        for (int i = 0; i < 6; i++) {
            try {
                coords[i] = parseInt(sender, args[i + 1]);
            } catch (NumberFormatException e) {
                IChatComponent msg = new ChatComponentTranslation(
                    "tempora.command.create_region.non_numeric_coordinate",
                    args[i + 1]);
                msg.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(msg);
                return;
            }
        }

        // Dimension ID: optional argument
        int dimID;
        if (args.length == 8) {
            try {
                dimID = parseInt(sender, args[7]);
            } catch (NumberFormatException e) {
                IChatComponent msg = new ChatComponentTranslation(
                    "tempora.command.create_region.non.numeric.dimension",
                    args[7]);
                msg.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(msg);
                return;
            }
        } else {
            dimID = sender.getEntityWorld().provider.dimensionId;
        }

        // Build region
        TemporaWorldRegion region = new TemporaWorldRegion(
            dimID,
            coords[0],
            coords[1],
            coords[2],
            coords[3],
            coords[4],
            coords[5]);

        region.setLabel(label);
        region.setRegionUUID(
            UUID.randomUUID()
                .toString());
        region.setRenderMode(RegionRenderMode.BLOCK_CHANGE);
        region.setRegionOriginTimeMs(System.currentTimeMillis());

        // todo remove!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        region.addWhitelistedLogger(TemporaEvents.blockChangeLogger);

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
            teleportChatComponent(
                region.getMinX(),
                region.getMinY(),
                region.getMinZ(),
                dimID,
                CommandUtils.TeleportType.EXACT),
            teleportChatComponent(
                region.getMaxX(),
                region.getMaxY(),
                region.getMaxZ(),
                dimID,
                CommandUtils.TeleportType.EXACT),
            teleportChatComponent(
                region.getMidX(),
                region.getMidY(),
                region.getMidZ(),
                dimID,
                CommandUtils.TeleportType.EXACT));

        msg.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(msg);
    }

    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.create_region.help.description");
    }

    @Override
    public String getExampleCommand() {
        return "test 0 0 0 5 5 5 0";
    }

    @Override
    public String getTranslationKeyBase() {
        return "tempora.command.create_region";
    }
}
