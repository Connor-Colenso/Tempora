package com.colen.tempora.commands;

import static com.colen.tempora.utils.ReflectionUtils.getAllTableColumns;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.config.CommandsConfig;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.RenderEventPacket;
import com.colen.tempora.loggers.generic.TemporaQueries;
import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.utils.CommandUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

// /tempora sql <SELECT ...>
// Read only query against a logger's db, for digging into stuff /tempora lookup can't express
public class SqlCommand extends TemporaSubcommand {

    public SqlCommand() {
        super("sql", "<SELECT ...>");
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsageError(sender);
            return;
        }

        String rawQuery = String.join(" ", args);

        GenericLogger<?> targetLogger = findTargetLogger(rawQuery);

        if (targetLogger == null) {
            ChatComponentTranslation msg = new ChatComponentTranslation(
                "tempora.command.sql.invalid_table",
                String.join(", ", TemporaLoggerManager.getAllLoggerNames()));
            msg.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(msg);
            return;
        }

        if (!isSelectQuery(rawQuery)) {
            ChatComponentTranslation msg = new ChatComponentTranslation("tempora.command.sql.select_only");
            msg.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(msg);

            return;
        }

        // Make sure the query selects every column we need
        List<ColumnDef> columns = getAllTableColumns(targetLogger);

        List<String> missing = findMissingColumns(rawQuery, columns);
        if (!missing.isEmpty()) {
            ChatComponentTranslation missingColsMsg = new ChatComponentTranslation(
                "tempora.command.sql.missing_columns",
                String.join(", ", missing));
            missingColsMsg.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(missingColsMsg);

            ChatComponentTranslation adviceMsg = new ChatComponentTranslation(
                "tempora.command.sql.missing_columns.advice");
            adviceMsg.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(adviceMsg);

            return;
        }

        // Disabled or failed to start, no db to read
        if (targetLogger.notStarted()) {
            sendError(sender, "tempora.command.sql.logger_off", targetLogger.getLoggerName());
            return;
        }

        GenericLogger<?> logger = targetLogger;

        // Read only, on the query thread. A mistake in the query is the sender's problem so show it to them instead of
        // failing the lookup
        TemporaQueries.submit(sender, () -> {
            try {
                return new SqlOutcome(executeReadOnlyQuery(logger, rawQuery), null);
            } catch (SQLException e) {
                TemporaQueries.throwIfTimedOut();
                return new SqlOutcome(null, e);
            }
        }, (target, outcome) -> showOutcome(target, logger, outcome));
    }

    private static void showOutcome(ICommandSender sender, GenericLogger<?> logger, SqlOutcome outcome) {
        if (outcome.error != null) {
            ChatComponentTranslation errorMsg = new ChatComponentTranslation(
                "tempora.command.sql.error",
                outcome.error.getMessage());
            errorMsg.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(errorMsg);
            return;
        }

        List<? extends GenericEventInfo> output = outcome.events;

        // First so a long response doesn't bury the info below
        for (GenericEventInfo eventData : output) {
            sender.addChatMessage(GenericLogger.chatLineOrPlaceholder(eventData));

            // Render info. Global events have no position, and only ones in the player's dimension since the client
            // draws nothing else
            if (eventData instanceof PositionalEventInfo positionalEvent && sender instanceof EntityPlayerMP player
                && positionalEvent.dimensionID == player.dimension) {
                RenderEventPacket.sendSafely(positionalEvent, player);
            }
        }

        if (output.isEmpty()) {
            ChatComponentTranslation noResultsMsg = new ChatComponentTranslation(
                "tempora.command.sql.no_results_in",
                logger.getLoggerName());
            noResultsMsg.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);
            sender.addChatMessage(noResultsMsg);
        } else if (output.size() == CommandsConfig.sqlMaxResults) {
            ChatComponentTranslation queryMsg = new ChatComponentTranslation(
                "tempora.command.sql.query_quantity_if_max",
                new ChatComponentNumber(output.size()),
                new ChatComponentNumber(CommandsConfig.sqlMaxResults));
            queryMsg.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);
            sender.addChatMessage(queryMsg);
        } else {
            ChatComponentTranslation queryMsg = new ChatComponentTranslation(
                "tempora.command.sql.query_quantity",
                new ChatComponentNumber(output.size()));
            queryMsg.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);
            sender.addChatMessage(queryMsg);
        }
    }

    // What the query thread found, the events or the error SQLite gave
    private static final class SqlOutcome {

        final List<? extends GenericEventInfo> events;
        final SQLException error;

        SqlOutcome(List<? extends GenericEventInfo> events, SQLException error) {
            this.events = events;
            this.error = error;
        }
    }

    // "it''s" counts as two strings next to each other, which is fine for blanking them out
    private static final Pattern STRING_LITERAL = Pattern.compile("'[^']*'");

    // Logger whose db this query reads, the first logger table it names. Each logger has its own db so a query can only
    // read one. Whatever's inside a string doesn't count, and SQLite ignores the case of table names
    private static @Nullable GenericLogger<?> findTargetLogger(String sql) {
        String withoutStrings = STRING_LITERAL.matcher(sql)
            .replaceAll("''");

        GenericLogger<?> first = null;
        int firstIndex = Integer.MAX_VALUE;
        for (GenericLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            Matcher name = Pattern
                .compile("\\b" + Pattern.quote(logger.getLoggerName()) + "\\b", Pattern.CASE_INSENSITIVE)
                .matcher(withoutStrings);
            if (name.find() && name.start() < firstIndex) {
                first = logger;
                firstIndex = name.start();
            }
        }
        return first;
    }

    // "select *" with any spacing or none, or "select t.*"
    private static final Pattern SELECT_STAR = Pattern.compile("\\bselect\\s*(\\w+\\.)?\\*");

    private static List<String> findMissingColumns(String sql, List<ColumnDef> required) {
        // SELECT * is fine
        String lowered = sql.toLowerCase(Locale.ROOT);
        if (SELECT_STAR.matcher(lowered)
            .find()) return Collections.emptyList();

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
        // Simple check that it starts with SELECT ignoring whitespace and case
        return sqlQuery.trim()
            .toLowerCase(Locale.ROOT)
            .startsWith("select");
    }

    // Query thread. Connection is read only and the lookup time limit stops anything that runs too long
    private static List<? extends GenericEventInfo> executeReadOnlyQuery(GenericLogger<?> logger, String sql)
        throws SQLException {

        try (PreparedStatement stmt = TemporaQueries.connection(logger)
            .prepareStatement(sql)) {

            stmt.setMaxRows(CommandsConfig.sqlMaxResults);

            List<? extends GenericEventInfo> events;
            try (ResultSet rs = stmt.executeQuery()) {
                events = logger.generateQueryResults(rs);
            }
            logger.loadExtras(events, TemporaQueries.connection(logger));
            return events;
        }
    }

    // Table names are the full logger names
    @Override
    public List<String> tabComplete(ICommandSender sender, String[] args) {
        return CommandUtils.completeLoggerNames(args);
    }

    @Override
    public String getExampleArgs() {
        // Player columns hold each player's number, player() gives it from a name or UUID
        return "SELECT * FROM PlayerPositionLogger WHERE playerUUID = player('Notch') AND y > 80";
    }

}
