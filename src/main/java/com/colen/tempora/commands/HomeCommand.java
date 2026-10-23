package com.colen.tempora.commands;

import static com.colen.tempora.TemporaEvents.PLAYER_POSITION;
import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.DimensionManager;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.command_base.CommandFilters;
import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.TemporaQueries;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

/**
 * /tempora home <player> [t:<time>] [dim:<id>]
 * Finds the chunk a player has spent the most time in, from their position history. Useful for finding their base.
 */
public class HomeCommand extends TemporaSubcommand {

    public HomeCommand() {
        super("home", "<player>", "[t:<time>]", "[dim:<id>]");
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sendUsageError(sender);
            return;
        }

        final String uuid = PlayerUtils.uuidFromName(args[0]);
        if (uuid == null) {
            sendError(sender, "tempora.command.unknown_player", args[0]);
            return;
        }

        CommandFilters filters = CommandFilters.parse(sender, args, 1, "t", "dim");
        if (filters == null) return;

        String time = filters.get("t");
        Long lookBackCutoffEpoch = time == null ? null
            : System.currentTimeMillis() - TimeUtils.convertToSeconds(time) * 1000L;

        String dim = filters.get("dim");
        Integer forcedDim = dim == null ? null : CommandBase.parseInt(sender, dim);

        GenericLogger<?> positionLogger = TemporaLoggerManager.getLogger(PLAYER_POSITION);
        if (positionLogger == null || positionLogger.notStarted()) {
            sendError(sender, "tempora.command.home.no_db");
            return;
        }

        // This query finds the player's most visited chunk (home chunk)
        // First, we group all position logs by chunk (x >> 4, z >> 4) and dimension
        // We count how many times the player was in each chunk
        // Then we sort by the number of visits and pick the top one (LIMIT 1)
        //
        // After that, we join this "hot chunk" back with the position table
        // This gives us all the rows that were inside the most visited chunk
        //
        // From those rows, we calculate:
        // - home_x: center of the chunk in x (cx * 16 + 8)
        // - home_y: average y value in that chunk
        // - home_z: center of the chunk in z (cz * 16 + 8)
        // - dimensionID: which dimension this chunk was in
        //
        // Optional filters are used to:
        // - restrict to a single dimension (forcedDim)
        // - only include recent data (lookBackCutoffEpoch)
        //
        // The player's UUID is passed in twice, once for each part of the query
        final String tbl = positionLogger.getLoggerName();
        StringBuilder sql = new StringBuilder().append("WITH hot AS (")
            .append("  SELECT (x>>4) AS cx, (z>>4) AS cz, dimensionID, COUNT(*) AS hits ")
            .append("  FROM ")
            .append(tbl)
            .append(" ")
            .append("  WHERE playerUUID = ? ");

        if (forcedDim != null) sql.append("AND dimensionID = ? ");
        if (lookBackCutoffEpoch != null) sql.append("AND timestamp >= ? ");
        sql.append("  GROUP BY cx, cz, dimensionID ")
            .append("  ORDER BY hits DESC LIMIT 1")
            .append(") ")
            .append("SELECT (hot.cx*16 + 8)  AS home_x, ")
            .append("       AVG(m.y)         AS home_y, ")
            .append("       (hot.cz*16 + 8)  AS home_z, ")
            .append("       hot.dimensionID  AS dimensionID ")
            .append("FROM   hot ")
            .append("JOIN   ")
            .append(tbl)
            .append(" m ")
            .append("  ON   (m.x>>4)=hot.cx AND (m.z>>4)=hot.cz AND m.dimensionID=hot.dimensionID ")
            .append("WHERE  m.playerUUID = ? ");
        if (lookBackCutoffEpoch != null) sql.append("AND m.timestamp >= ? ");

        // On the query thread. Null if the player has no positions matching the filters.
        TemporaQueries.Query<HomeChunk> query = () -> {
            try (PreparedStatement stmt = TemporaQueries.connection(positionLogger)
                .prepareStatement(sql.toString())) {

                int i = 1;

                // params for hot CTE
                stmt.setString(i++, uuid);
                if (forcedDim != null) stmt.setInt(i++, forcedDim);
                if (lookBackCutoffEpoch != null) stmt.setLong(i++, lookBackCutoffEpoch);

                // params for outer WHERE
                stmt.setString(i++, uuid);
                if (lookBackCutoffEpoch != null) stmt.setLong(i, lookBackCutoffEpoch);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next() || rs.getObject("home_x") == null) return null;

                    return new HomeChunk(
                        rs.getDouble("home_x"),
                        rs.getDouble("home_y"),
                        rs.getDouble("home_z"),
                        rs.getInt("dimensionID"));
                }
            } catch (SQLException e) {
                TemporaQueries.throwIfTimedOut();
                throw e;
            }
        };

        TemporaQueries.submit(sender, query, (target, home) -> {
            if (home == null) {
                target.addChatMessage(new ChatComponentTranslation("tempora.command.home.no_data"));
                return;
            }

            IChatComponent tpLink = teleportChatComponent(
                home.x,
                home.y,
                home.z,
                home.dimension,
                CommandUtils.TeleportType.EXACT);

            target.addChatMessage(
                new ChatComponentTranslation(
                    "tempora.command.home.result",
                    PlayerUtils.playerNameFromUUID(uuid),
                    home.dimension,
                    tpLink));
        });
    }

    private static final class HomeChunk {

        final double x, y, z;
        final int dimension;

        HomeChunk(double x, double y, double z, int dimension) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
        }
    }

    @Override
    public List<String> tabComplete(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return PlayerUtils.getTabCompletionForPlayerNames(args[0]);
        }

        List<String> dimensions = new ArrayList<>();
        for (Integer id : DimensionManager.getStaticDimensionIDs()) {
            dimensions.add(String.valueOf(id));
        }

        Map<String, List<String>> suggestions = new LinkedHashMap<>();
        suggestions.put("t", Arrays.asList("1d", "1w", "1mo"));
        suggestions.put("dim", dimensions);
        return CommandFilters.complete(args, suggestions);
    }

    @Override
    public String getExampleArgs() {
        return "Notch t:1mo dim:0";
    }
}
