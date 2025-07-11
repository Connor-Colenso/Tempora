package com.colen.tempora.commands;

import static com.colen.tempora.commands.CommandConstants.ONLY_IN_GAME;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;

public class QuerySQLCommand extends CommandBase {

    public static int MAX_RESULTS_TO_SHOW = 100;

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
            // This is likely executed by a terminal, so translation is meaningless.
            sender.addChatMessage(new ChatComponentText(ONLY_IN_GAME));
            return;
        }

        String rawQuery = String.join(" ", args);

        // Find logger by name.
        GenericPositionalLogger<?> targetLogger = null;
        for (GenericPositionalLogger<?> logger : GenericPositionalLogger.getLoggerList()) {
            if (rawQuery.contains(logger.getSQLTableName())) {
                targetLogger = logger;
                break;
            }
        }

        if (targetLogger == null) {
            ChatComponentTranslation msg = new ChatComponentTranslation(
                "tempora.command.querysql.invalid_table",
                GenericPositionalLogger.getAllLoggerNames());
            msg.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(msg);
            return;
        }

        if (!isSelectQuery(rawQuery)) {
            ChatComponentTranslation msg = new ChatComponentTranslation("tempora.command.querysql.select_only");
            msg.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(msg);

            return;
        }

        // Execute the query read-only
        try {
            // Ensure the user has all the columns needed
            List<ColumnDef> columns = targetLogger.getAllTableColumns();

            List<String> missing = findMissingColumns(rawQuery, columns);
            if (!missing.isEmpty()) {
                ChatComponentTranslation missingColsMsg = new ChatComponentTranslation(
                    "tempora.command.querysql.missing_columns",
                    String.join(", ", missing));
                missingColsMsg.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(missingColsMsg);

                ChatComponentTranslation adviceMsg = new ChatComponentTranslation(
                    "tempora.command.querysql.missing_columns.advice");
                adviceMsg.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(adviceMsg);

                return;
            }

            List<IChatComponent> output = executeReadOnlyQuery(targetLogger, rawQuery, entityPlayerMP);

            // We do this first, to not bury the info below, in case of a long response.
            for (IChatComponent message : output) {
                sender.addChatMessage(message);
            }

            ChatComponentTranslation queryFeedbackMsg = new ChatComponentTranslation(
                "tempora.command.querysql.query_display",
                rawQuery);
            queryFeedbackMsg.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);

            if (output.isEmpty()) {
                ChatComponentTranslation noResultsMsg = new ChatComponentTranslation(
                    "tempora.command.querysql.no_results_in",
                    targetLogger.getSQLTableName());
                noResultsMsg.getChatStyle()
                    .setColor(EnumChatFormatting.GRAY);
                sender.addChatMessage(noResultsMsg);
            } else if (output.size() == MAX_RESULTS_TO_SHOW) {
                ChatComponentTranslation queryMsg = new ChatComponentTranslation(
                    "tempora.command.querysql.query_quantity_if_max",
                    output.size(),
                    MAX_RESULTS_TO_SHOW);
                queryMsg.getChatStyle()
                    .setColor(EnumChatFormatting.GRAY);
                sender.addChatMessage(queryMsg);
            } else {
                ChatComponentTranslation queryMsg = new ChatComponentTranslation(
                    "tempora.command.querysql.query_quantity",
                    output.size());
                queryMsg.getChatStyle()
                    .setColor(EnumChatFormatting.GRAY);
                sender.addChatMessage(queryMsg);
            }

        } catch (SQLException e) {
            ChatComponentTranslation errorMsg = new ChatComponentTranslation(
                "tempora.command.querysql.error",
                e.getMessage());
            errorMsg.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(errorMsg);
        } catch (Exception e) {
            ChatComponentTranslation errorMsg = new ChatComponentTranslation(
                "tempora.command.querysql.generic_unknown_error",
                e.getMessage());
            errorMsg.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(errorMsg);
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
            if (!p.matcher(lowered)
                .find()) {
                missing.add(col.name);
            }
        }
        return missing;
    }

    private static boolean isSelectQuery(String sqlQuery) {
        // Simple check that it starts with SELECT ignoring whitespace & case
        return sqlQuery.trim()
            .toLowerCase()
            .startsWith("select");
    }

    private List<IChatComponent> executeReadOnlyQuery(GenericPositionalLogger<?> logger, String sql,
        EntityPlayer queryIssuerEntityPlayer) throws SQLException {
        List<IChatComponent> rows = new ArrayList<>();

        try (Connection roConn = logger.getReadOnlyConnection();
            PreparedStatement stmt = roConn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery()) {

            stmt.setMaxRows(MAX_RESULTS_TO_SHOW);

            List<GenericQueueElement> queryResults = logger.generateQueryResults(rs);

            for (GenericQueueElement queueElement : queryResults) {
                rows.add(
                    queueElement.localiseText(
                        queryIssuerEntityPlayer.getPersistentID()
                            .toString()));
            }
        }

        return rows;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        List<String> names = GenericPositionalLogger.getAllLoggerNames();

        return CommandBase.getListOfStringsMatchingLastWord(args, names.toArray(new String[0]));
    }
}
