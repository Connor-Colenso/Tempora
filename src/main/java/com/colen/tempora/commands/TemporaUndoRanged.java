package com.colen.tempora.commands;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.Tempora.NETWORK;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.TimeUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

public class TemporaUndoRanged extends CommandBase {

    // Todo clear on world exit.
    private static final Map<String, List<? extends GenericQueueElement>> PENDING_UNDOS = new ConcurrentHashMap<>();
    private static final Map<String, String> PENDING_UNDOS_LOGGER_NAMES = new ConcurrentHashMap<>();

    public static int MAX_RANGE;

    @Override
    public String getCommandName() {
        return "tempora_undo_ranged";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/tempora_undo_ranged <radius> <time> <logger_name>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        if (!(sender instanceof EntityPlayerMP player)) {
            sender.addChatMessage(new ChatComponentTranslation("This command may only be used by a player in-game."));
            return;
        }

        if (args.length < 1) throw new WrongUsageException(getCommandUsage(sender));

        // ==== Confirmation path ====
        if (args[0].equalsIgnoreCase("confirm")) {
            handleConfirmation(player, args);
            return;
        }

        // ==== Preview path ====
        if (args.length != 3) throw new WrongUsageException(getCommandUsage(sender));

        int radius = parseInt(sender, args[0]);
        long seconds = TimeUtils.convertToSeconds(args[1]);
        String loggerName = args[2];

        if (radius < 0) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.range.negative"));
            return;
        }

        if (radius > MAX_RANGE) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.undo.range.too_large", MAX_RANGE));
            radius = MAX_RANGE;
        }

        GenericPositionalLogger<?> logger = TemporaLoggerManager.getLogger(loggerName);
        if (logger == null) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.undo.wrong.logger", loggerName));
            return;
        }

        if (!logger.isUndoEnabled()) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.undo.not_undoable", loggerName));
            return;
        }

        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - seconds * 1000L);
        String table = logger.getLoggerName();

        // Attempt at optimised SQL query
        String sql = "SELECT t.* FROM " + table
            + " t "
            + "JOIN (SELECT x,y,z,MIN(timestamp) ts FROM "
            + table
            + " WHERE x BETWEEN ?-? AND ?+? AND y BETWEEN ?-? AND ?+? "
            + " AND z BETWEEN ?-? AND ?+? AND dimensionID=? AND timestamp>=? "
            + " GROUP BY x,y,z) oldest "
            + "ON t.x=oldest.x AND t.y=oldest.y AND t.z=oldest.z AND t.timestamp=oldest.ts "
            + "ORDER BY t.timestamp ASC";

        List<? extends GenericQueueElement> results;

        try (Connection conn = logger.getDatabaseManager()
            .getReadOnlyConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, (int) player.posX);
            ps.setInt(2, radius);
            ps.setInt(3, (int) player.posX);
            ps.setInt(4, radius);

            ps.setInt(5, (int) player.posY);
            ps.setInt(6, radius);
            ps.setInt(7, (int) player.posY);
            ps.setInt(8, radius);

            ps.setInt(9, (int) player.posZ);
            ps.setInt(10, radius);
            ps.setInt(11, (int) player.posZ);
            ps.setInt(12, radius);

            ps.setInt(13, player.dimension);
            ps.setTimestamp(14, cutoff);

            try (ResultSet rs = ps.executeQuery()) {
                results = logger.generateQueryResults(rs);
            }
        } catch (Exception e) {
            LOG.error("Undo preview DB error", e);
            sender.addChatMessage(new ChatComponentTranslation("tempora.undo.failed", e.getMessage()));
            return;
        }

        if (results.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.undo.nothing", loggerName));
            return;
        }

        // Send preview markers
        for (GenericQueueElement event : results) {
            NETWORK.sendTo(event, player);
        }

        // Store preview results
        String uuid = UUID.randomUUID()
            .toString();
        PENDING_UNDOS.put(uuid, results);
        PENDING_UNDOS_LOGGER_NAMES.put(uuid, loggerName);

        IChatComponent click = new ChatComponentTranslation("tempora.undo.preview.confirm").setChatStyle(
            new ChatStyle().setColor(EnumChatFormatting.AQUA)
                .setChatHoverEvent(
                    new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentTranslation("tempora.undo.preview.highlight")))
                .setChatClickEvent(
                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tempora_undo_ranged confirm " + uuid)));

        sender.addChatMessage(new ChatComponentTranslation("tempora.undo.preview", click));
    }

    private void handleConfirmation(EntityPlayerMP sender, String[] args) {
        if (args.length != 2) throw new WrongUsageException(getCommandUsage(sender));

        String uuid = args[1];

        List<? extends GenericQueueElement> stored = PENDING_UNDOS.get(uuid);
        String loggerName = PENDING_UNDOS_LOGGER_NAMES.get(uuid);

        if (stored == null || loggerName == null) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.event.not.found.undo.ranged", uuid));
            return;
        }

        GenericPositionalLogger<?> logger = TemporaLoggerManager.getLogger(loggerName);

        if (!logger.isUndoEnabled()) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.undo.not_undoable", loggerName));
            return;
        }

        long start = System.currentTimeMillis();
        logger.undoEvents(stored, sender);
        long duration = System.currentTimeMillis() - start;

        TimeUtils.DurationParts p = TimeUtils.relativeTimeAgoFormatter(duration);

        sender.addChatMessage(
            new ChatComponentTranslation(
                "tempora.undo.success.ranged",
                stored.size(),
                new ChatComponentNumber(p.time),
                new ChatComponentTranslation(p.translationKey)));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 3) {
            return CommandUtils.completeLoggerNames(args);
        }
        return null;
    }
}
