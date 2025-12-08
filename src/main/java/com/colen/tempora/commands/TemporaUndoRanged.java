package com.colen.tempora.commands;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.loggers.optional.ISupportsUndo;
import com.colen.tempora.utils.TimeUtils;

public class TemporaUndoRanged extends CommandBase {

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
        if (args.length != 3) throw new WrongUsageException(getCommandUsage(sender));

        if (!(sender instanceof EntityPlayerMP)) return; // Not a player, nothing to do

        EntityPlayerMP player = (EntityPlayerMP) sender;

        String loggerName = args[2];
        GenericPositionalLogger<?> genericLogger = GenericPositionalLogger.getLogger(loggerName);

        if (genericLogger == null) {
            throw new WrongUsageException("tempora.command.undo.wrong.logger", loggerName);
        }

        if (!(genericLogger instanceof ISupportsUndo supportsUndo)) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.undo.not_undoable", loggerName));
            return;
        }

        // Parse numeric arguments
        int radius = parseInt(sender, args[0]);
        long seconds = TimeUtils.convertToSeconds(args[1].toLowerCase());

        if (radius < 0) {
            player.addChatMessage(new ChatComponentTranslation("tempora.range.negative"));
            return;
        }

        // Compute timestamp cutoff
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - seconds * 1000L);

        // Table name
        String table = genericLogger.getSQLTableName();

        // Optimised SQL: RANGE instead of ABS, avoids full scan
        String sql = String.format("""
                SELECT t.*
                FROM %s t
                JOIN (
                    SELECT x, y, z, MIN(timestamp) AS ts
                    FROM %s
                    WHERE x BETWEEN ? - ? AND ? + ?
                      AND y BETWEEN ? - ? AND ? + ?
                      AND z BETWEEN ? - ? AND ? + ?
                      AND dimensionID = ?
                      AND timestamp >= ?
                    GROUP BY x, y, z
                ) oldest
                  ON t.x = oldest.x
                 AND t.y = oldest.y
                 AND t.z = oldest.z
                 AND t.timestamp = oldest.ts
                ORDER BY t.timestamp ASC;
            """, table, table);

        try (Connection conn = genericLogger.getReadOnlyConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {

            // Bind parameters (range conditions)
            ps.setDouble(1, player.posX);
            ps.setInt(2, radius);
            ps.setDouble(3, player.posX);
            ps.setInt(4, radius);

            ps.setDouble(5, player.posY);
            ps.setInt(6, radius);
            ps.setDouble(7, player.posY);
            ps.setInt(8, radius);

            ps.setDouble(9, player.posZ);
            ps.setInt(10, radius);
            ps.setDouble(11, player.posZ);
            ps.setInt(12, radius);

            ps.setInt(13, player.dimension);
            ps.setTimestamp(14, cutoff);

            // Execute
            ResultSet rs = ps.executeQuery();
            List<GenericQueueElement> results = genericLogger.generateQueryResults(rs);

            if (results.isEmpty()) {
                sender.addChatMessage(new ChatComponentTranslation("tempora.command.undo.nothing", loggerName));
                return;
            }

            long start = System.currentTimeMillis();

            // Undo events
            supportsUndo.undoEvents(results);

            // Record outcome to player who executed undo.
            long end = System.currentTimeMillis();
            long duration = end - start;
            TimeUtils.DurationParts p = TimeUtils.formatShortDuration(duration);

            sender.addChatMessage(
                new ChatComponentTranslation(
                    "tempora.undo.success.ranged",
                    // todo fix that some events count as "undone" even when they threw an error or such. Probably need
                    // a true/false for success, but then requires a pair etc. Something to think about.
                    results.size(), // Todo format number for larger sizes i.e. 1000+. Need to move formatting to
                                    // NHLib...
                    p.value,
                    new ChatComponentTranslation(p.unitKey)));

        } catch (Exception e) {
            e.printStackTrace();
            sender.addChatMessage(new ChatComponentTranslation("tempora.undo.failed", e.getMessage()));
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 3) {
            String partialFilter = args[2].toLowerCase();
            List<String> matchingOptions = new ArrayList<>();
            for (String option : GenericPositionalLogger.getAllLoggerNames()) {
                if (option.toLowerCase()
                    .startsWith(partialFilter)) {
                    matchingOptions.add(option);
                }
            }
            return matchingOptions;
        }
        return null; // Return null when there are no matches.
    }

}
