package com.colen.tempora.commands.region;

import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.DimensionManager;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.networking.PlayerPackets;
import com.colen.tempora.networking.packets.PacketShowRegionInWorld;
import com.colen.tempora.regions.TemporaRegionRegistry;
import com.colen.tempora.regions.TemporaWorldRegion;
import com.colen.tempora.rendering.regions.RegionRenderMode;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

// /tempora region create <name> <x1> <y1> <z1> <x2> <y2> <z2> [dim_ID]
// Box between two corner blocks, both included. Coords can be relative to the player, e.g. ~-50. New regions have no
// rules so they change nothing until /tempora region set gives them some
public class RegionCreateCommand extends TemporaSubcommand {

    public RegionCreateCommand() {
        super("create", "<name>", "<x1> <y1> <z1>", "<x2> <y2> <z2>", "[dim_ID]");
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        // Name + 6 coords + optional dim ID
        if (args.length != 7 && args.length != 8) {
            sendUsageError(sender);
            return;
        }

        final String name = args[0];
        if (TemporaRegionRegistry.getByLabel(name) != null) {
            sendError(sender, "tempora.command.region.create.name_taken", name);
            return;
        }

        EntityPlayerMP player = sender instanceof EntityPlayerMP p ? p : null;
        // Doubles so a relative offset and the + 1 below can't overflow on huge coords
        double[] coords = new double[6];
        for (int i = 0; i < 6; i++) {
            coords[i] = parseCoordinate(sender, player, i % 3, args[i + 1]);
        }

        int dimID = args.length == 8 ? CommandBase.parseInt(sender, args[7])
            : sender.getEntityWorld().provider.dimensionId;
        if (!DimensionManager.isDimensionRegistered(dimID)) {
            sendError(sender, "tempora.dimension.invalid", new ChatComponentNumber(dimID));
            return;
        }

        // Both corners inclusive like /fill, so "0 0 0 4 4 4" is a 5x5x5 cube. Max bounds are exclusive internally,
        // hence the + 1
        TemporaWorldRegion region = new TemporaWorldRegion(
            dimID,
            Math.min(coords[0], coords[3]),
            Math.min(coords[1], coords[4]),
            Math.min(coords[2], coords[5]),
            Math.max(coords[0], coords[3]) + 1,
            Math.max(coords[1], coords[4]) + 1,
            Math.max(coords[2], coords[5]) + 1);

        region.setLabel(name);
        region.setRegionUUID(
            UUID.randomUUID()
                .toString());
        region.setRenderMode(RegionRenderMode.REGION);
        region.setRegionOriginTimeMs(System.currentTimeMillis());
        region.setPlayerAuthorUUID(
            player == null ? UNKNOWN_PLAYER_NAME
                : player.getUniqueID()
                    .toString());

        TemporaRegionRegistry.add(region);

        IChatComponent created = new ChatComponentTranslation(
            "tempora.command.region.create.success",
            name,
            region.getMinCornerChatComponent(),
            region.getMaxCornerChatComponent(),
            region.getMidpointChatComponent());
        created.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(created);

        // Most regions get made to log block changes in them, so offer that
        GenericLogger<?> blockLogger = TemporaLoggerManager.getLogger(TemporaEvents.BLOCK_CHANGE);
        String blockLoggingCommand = TemporaCommand.REGION_SET.command(name, blockLogger.getShortName(), "on");
        IChatComponent suggestion = new ChatComponentTranslation("tempora.command.region.create.suggest_block_logging");
        suggestion.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, blockLoggingCommand))
            .setChatHoverEvent(
                new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ChatComponentTranslation("tempora.command.region.create.suggest_block_logging.hover")));

        IChatComponent noRules = new ChatComponentTranslation("tempora.command.region.create.no_rules", suggestion);
        noRules.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        sender.addChatMessage(noRules);

        if (player != null) {
            PlayerPackets.sendTo(new PacketShowRegionInWorld.RegionMsg(region), player);
        }
    }

    // Block coord, or one relative to the player's block like "~" or "~-50". Axis 0, 1, 2 is x, y, z. Throws a
    // CommandException (shown to the sender) if a relative coord is used without a player
    private static double parseCoordinate(ICommandSender sender, @Nullable EntityPlayerMP player, int axis,
        String arg) {
        if (!arg.startsWith("~")) return CommandBase.parseInt(sender, arg);

        if (player == null) throw new CommandException("tempora.command.region.create.relative_needs_player");

        double position = axis == 0 ? player.posX : axis == 1 ? player.posY : player.posZ;
        int offset = arg.length() == 1 ? 0 : CommandBase.parseInt(sender, arg.substring(1));
        return (double) MathHelper.floor_double(position) + offset;
    }

    @Override
    public List<String> tabComplete(ICommandSender sender, String[] args) {
        // Offer "~" for coords to show relative ones work
        if (args.length >= 2 && args.length <= 7 && args[args.length - 1].isEmpty()) {
            return Collections.singletonList("~");
        }
        return null;
    }

    @Override
    public String getExampleArgs() {
        return "spawn ~-50 0 ~-50 ~50 255 ~50";
    }
}
