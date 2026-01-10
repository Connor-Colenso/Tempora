package com.colen.tempora.commands;

import static com.colen.tempora.Tempora.NETWORK;

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
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.loggers.generic.RenderEventPacket;
import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.utils.CommandUtils;

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
            sender.addChatMessage(CommandUtils.playerOnly());
            return;
        }

        String rawQuery = String.join(" ", args);

        // Find logger by name.
        GenericPositionalLogger<?> targetLogger = null;
        for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            if (rawQuery.contains(logger.getLoggerName())) {
                targetLogger = logger;
                break;
            }
        }

        if (targetLogger == null) {
            ChatComponentTranslation msg = new ChatComponentTranslation(
                "tempora.command.querysql.invalid_table",
                TemporaLoggerManager.getAllLoggerNames());
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
            List<ColumnDef> columns = targetLogger.getDatabaseManager()
                .getAllTableColumns();

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

            List<? extends GenericQueueElement> output = executeReadOnlyQuery(targetLogger, rawQuery);

            // We do this first, to not bury the info below, in case of a long response.
            for (GenericQueueElement eventData : output) {
                sender.addChatMessage(
                    eventData.localiseText(
                        entityPlayerMP.getPersistentID()
                            .toString()));

                // Render info.
                NETWORK.sendTo(new RenderEventPacket(eventData), entityPlayerMP);
            }

            ChatComponentTranslation queryFeedbackMsg = new ChatComponentTranslation(
                "tempora.command.querysql.query_display",
                rawQuery);
            queryFeedbackMsg.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);

            if (output.isEmpty()) {
                ChatComponentTranslation noResultsMsg = new ChatComponentTranslation(
                    "tempora.command.querysql.no_results_in",
                    targetLogger.getLoggerName());
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

    private List<? extends GenericQueueElement> executeReadOnlyQuery(GenericPositionalLogger<?> logger, String sql)
        throws SQLException {

        try (Connection roConn = logger.getDatabaseManager()
            .getReadOnlyConnection();
            PreparedStatement stmt = roConn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery()) {

            stmt.setMaxRows(MAX_RESULTS_TO_SHOW);

            return logger.generateQueryResults(rs);
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return CommandUtils.completeLoggerNames(args);
    }
}
