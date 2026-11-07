package com.colen.tempora.utils;

import static com.colen.tempora.Tempora.LOG;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.chat.ChatComponentTeleport;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.commands.TemporaCommand;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

public class CommandUtils {

    public static final int OP_ONLY = 2;

    // Whether this player can use /tempora, and so the wand, teleport clicks and our admin warnings. Asks the server's
    // list of commands the player can use, which is what permission mods change (ServerUtilities ranks go there), so it
    // always agrees with /tempora itself. Without one it's just /tempora's op check
    //
    // Building that list runs every mod's permission check, and those can throw (WarpTheory NPEs on a player who isn't
    // in the player list). This gets called from the server tick, so one bad check can't take it down. Falls back to
    // the op check, so someone only allowed through a rank loses access while a check is throwing
    public static boolean mayUseTempora(EntityPlayerMP player) {
        ICommandManager commands = player.mcServer.getCommandManager();
        Object tempora = commands.getCommands()
            .get(TemporaCommand.NAME);
        if (tempora == null) return player.canCommandSenderUseCommand(OP_ONLY, TemporaCommand.NAME);
        try {
            return commands.getPossibleCommands(player)
                .contains(tempora);
        } catch (RuntimeException e) {
            if (!warnedAboutCommandCheck) {
                warnedAboutCommandCheck = true;
                LOG.warn(
                    "Another mod's command permission check threw for {}, so /tempora access is using the op check.",
                    player.getCommandSenderName(),
                    e);
            }
            return player.canCommandSenderUseCommand(OP_ONLY, TemporaCommand.NAME);
        }
    }

    // Only the first, this runs on every tab complete and could fail every time
    private static boolean warnedAboutCommandCheck;

    @NotNull
    public static List<String> completeLoggerNames(String[] args) {
        return CommandBase.getListOfStringsMatchingLastWord(
            args,
            TemporaLoggerManager.getAllLoggerNames()
                .toArray(new String[0]));
    }

    // Not localised since the console can call it and it has no localisation
    public static final String ONLY_IN_GAME = "This command may only be executed by a player in-game.";

    public static IChatComponent playerOnly() {
        IChatComponent errorMessage = new ChatComponentText(ONLY_IN_GAME);
        errorMessage.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        return errorMessage;
    }

    public static IChatComponent regionNotFound(String label) {
        IChatComponent errorMessage = new ChatComponentTranslation("tempora.region.not_found", label);
        errorMessage.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        return errorMessage;
    }

    public enum TeleportType {
        BLOCK,
        EXACT
    }

    public static @NotNull IChatComponent teleportChatComponent(double x, double y, double z, final int dimID,
        final TeleportType teleportType) {

        // Titled in the coords' colour since GTNH tooltips draw a grey first line white
        IChatComponent title = new ChatComponentTranslation("tempora.teleport.hover");
        title.getChatStyle()
            .setColor(EnumChatFormatting.AQUA);
        IChatComponent dimension = new ChatComponentTranslation(
            "tempora.teleport.dimension",
            GenericUtils.getDimensionName(dimID),
            new ChatComponentNumber(dimID));
        IChatComponent hoverText = teleportType == TeleportType.EXACT ? EventChat.titledHover(
            title,
            dimension,
            new ChatComponentTranslation(
                "tempora.teleport.exact",
                new ChatComponentNumber(x),
                new ChatComponentNumber(y),
                new ChatComponentNumber(z)))
            : EventChat.titledHover(title, dimension);

        // Offset to put the player in the middle of the block. Still shown as that block since the display rounds down
        if (teleportType == TeleportType.BLOCK) {
            x += 0.5;
            y += 0.5;
            z += 0.5;
        }

        IChatComponent display = new ChatComponentTeleport(x, y, z, dimID);
        display.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        return display;
    }
}
