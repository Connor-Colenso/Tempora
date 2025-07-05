package com.colen.tempora.logging.commands;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.ISerializable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;

public class QuerySQLCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "querysql";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/querysql <LoggerName> \"<SQL SELECT query>\"";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP only
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        if (!(sender instanceof EntityPlayerMP entityPlayerMP)) {
            sender.addChatMessage(new ChatComponentText("This command can only be run by a player."));
            return;
        }

        if (args.length < 2) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Usage: " + getCommandUsage(sender)));
            return;
        }

        String loggerName = args[0];
        String rawQuery = joinArgsFromIndex(args, 1);

        // Must be wrapped in quotes, e.g. "SELECT * FROM ..."
        if (!rawQuery.startsWith("\"") || !rawQuery.endsWith("\"")) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "SQL query must be wrapped in quotes."));
            return;
        }

        // Find logger by name
        GenericPositionalLogger<?> targetLogger = null;
        for (GenericPositionalLogger<?> logger : GenericPositionalLogger.getLoggerList()) {
            if (logger.getSQLTableName().equalsIgnoreCase(loggerName)) {
                targetLogger = logger;
                break;
            }
        }

        if (targetLogger == null) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Logger '" + loggerName + "' not found."));
            return;
        }

        // Strip quotes and get SQL query.
        String sql = rawQuery.substring(1, rawQuery.length() - 1).trim();

        if (!isSelectQuery(sql)) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Only SELECT queries are allowed."));
            return;
        }

        // Execute the query read-only
        try {
            // Ensure the user has all the columns needed
            List<ColumnDef> columns = new ArrayList<>(targetLogger.getTableColumns());
            columns.addAll(GenericPositionalLogger.getDefaultColumns());

            List<String> missing = findMissingColumns(sql, columns);
            if (!missing.isEmpty()) {
                String msg = EnumChatFormatting.RED + "Query missing required columns: "
                    + String.join(", ", missing);
                sender.addChatMessage(new ChatComponentText(msg));
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "It is highly recommended to use SELECT * instead of specifying columns. "));
                return;
            }

            List<String> output = executeReadOnlyQuery(targetLogger, sql, entityPlayerMP);

            if (output.isEmpty()) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "No results."));
            } else {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "Query results:"));
                for (String line : output) {
                    sender.addChatMessage(new ChatComponentText(line));
                }
            }

        } catch (Exception e) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Error: " + e.getMessage()));
        }
    }

    private static List<String> findMissingColumns(String sql, List<ColumnDef> required) {
        // If the query explicitly selects every column with *, accept it.
        String lowered = sql.toLowerCase(Locale.ROOT);
        if (lowered.contains("select *")) return Collections.emptyList();

        List<String> missing = new ArrayList<>();

        // Check each required column name appears as its own word
        for (ColumnDef col : required) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(col.name.toLowerCase(Locale.ROOT)) + "\\b");
            if (!p.matcher(lowered).find()) {
                missing.add(col.name);
            }
        }
        return missing;
    }

    private static boolean isSelectQuery(String sql) {
        // Simple check that it starts with SELECT ignoring whitespace & case
        return sql.trim().toLowerCase().startsWith("select");
    }

    private static String joinArgsFromIndex(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private List<String> executeReadOnlyQuery(GenericPositionalLogger<?> logger, String sql, EntityPlayer entityPlayer) throws SQLException {
        List<String> rows = new ArrayList<>();

        try (Connection roConn = logger.getReadOnlyConnection();
             PreparedStatement stmt = roConn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<ISerializable> packets = logger.generateQueryResults(rs);

            if (packets.isEmpty()) {
                entityPlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.GRAY + "No results found in "
                        + logger.getSQLTableName()
                        + '.'));

                entityPlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "Query: " + sql));
            }

            for (ISerializable p : packets) {
                entityPlayer.addChatMessage(p.localiseText(entityPlayer.getPersistentID().toString()));
            }
        }

        return rows;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (GenericPositionalLogger<?> logger : GenericPositionalLogger.getLoggerList()) {
                String name = logger.getSQLTableName();
                if (name.toLowerCase().startsWith(partial)) {
                    matches.add(name);
                }
            }
            return matches;
        }

        // No tab complete for query string (args.length >= 2)
        return null;
    }
}
