package com.colen.tempora.commands;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.DimensionManager;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.commands.command_base.CommandFilters;
import com.colen.tempora.commands.command_base.SearchSummary;
import com.colen.tempora.commands.command_base.TemporaSubcommand;
import com.colen.tempora.loggers.generic.PlayerIDs;
import com.colen.tempora.loggers.generic.TemporaQueries;
import com.colen.tempora.loggers.player_position.PlayerPositionLogger;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.GenericUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeWindow;

// /tempora home <player> [t:<time>] [dim:<id>] [n:<count>]
// Places a player spends the most time from their position history, busiest first. Good for finding their base and
// telling it apart from an AFK farm or spawn
//
// Positions get logged at a fixed interval so the count in a chunk is how long they spent there. Each place is the 3x3
// chunks around its busiest chunk so a base over a chunk border counts once. Places get picked busiest first, skipping
// any too close to one already picked, until there are enough or whatever's left is just places they passed through
public class HomeCommand extends TemporaSubcommand {

    public static final int DEFAULT_COUNT = 3;
    private static final int MAX_COUNT = 10;

    // Chunks either side of a place's busiest chunk that count as the same place
    static final int AREA_RADIUS = 1;
    // A chunk this close or closer to a place already listed can't be another one
    static final int SEPARATION = 4;
    // Less of their time than this is just passing through
    static final double MIN_SHARE = 0.01;

    // Chunk a coord is in, rounded down. SQLite's REAL -> INTEGER rounds towards zero, which would put x = -16.5 in
    // chunk -1 not -2
    private static final String CHUNK_X = "((CAST(x AS INTEGER) - (x < CAST(x AS INTEGER))) >> 4)";
    private static final String CHUNK_Z = "((CAST(z AS INTEGER) - (z < CAST(z AS INTEGER))) >> 4)";

    public HomeCommand() {
        super("home", "<player>", "[t:<time>]", "[dim:<id>]", "[n:<count>]");
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

        CommandFilters filters = CommandFilters.parse(this, sender, args, 1, "t", "dim", "n");
        if (filters == null) return;

        String time = filters.get("t");
        TimeWindow window = time == null ? TimeWindow.allTime() : TimeWindow.parse(time);

        String dim = filters.get("dim");
        Integer forcedDim = dim == null ? null : CommandBase.parseInt(sender, dim);

        int count = CommandBase.parseIntBounded(sender, filters.get("n", String.valueOf(DEFAULT_COUNT)), 1, MAX_COUNT);

        if (TemporaEvents.playerPositionLogger.notStarted()) {
            sendError(sender, "tempora.command.home.no_db");
            return;
        }

        SearchSummary summary = new SearchSummary(this, filters).add("t", "all")
            .add("dim", "all")
            .add("n", String.valueOf(DEFAULT_COUNT));

        TemporaQueries.submit(sender, () -> findHotSpots(uuid, window, forcedDim, count), (target, spots) -> {
            if (!spots.isEmpty()) {
                target.addChatMessage(EventChat.line("tempora.command.home.header", EventChat.player(uuid)));
                for (int i = 0; i < spots.size(); i++) {
                    target.addChatMessage(spotLine(i + 1, spots.get(i)));
                }
                if (spots.size() < count) {
                    target.addChatMessage(EventChat.line("tempora.command.home.no_more"));
                }
            }
            target.addChatMessage(summary.line(!spots.isEmpty()));
        });
    }

    // Places a player spent the most time, busiest first, up to count. Query thread. Empty if they have no positions
    // matching the filters
    public static List<HotSpot> findHotSpots(String uuid, TimeWindow window, @Nullable Integer dimension, int count)
        throws SQLException {
        PlayerPositionLogger logger = TemporaEvents.playerPositionLogger;
        String sql = "SELECT dimensionID, " + CHUNK_X
            + " AS cx, "
            + CHUNK_Z
            + " AS cz, COUNT(*) AS hits, MAX(timestamp) AS lastSeen, x, y, z FROM "
            + logger.getLoggerName()
            + " WHERE playerUUID = ? AND timestamp >= ? AND timestamp <= ?"
            + (dimension == null ? "" : " AND dimensionID = ?")
            + " GROUP BY dimensionID, cx, cz";

        long now = System.currentTimeMillis();
        List<ChunkHits> chunks = new ArrayList<>();
        try (PreparedStatement stmt = TemporaQueries.connection(logger)
            .prepareStatement(sql)) {
            // Position table holds the player's number not their UUID
            stmt.setInt(1, PlayerIDs.lookupID(uuid));
            stmt.setLong(2, window.oldestMillis(now));
            stmt.setLong(3, window.newestMillis(now));
            if (dimension != null) stmt.setInt(4, dimension);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    // Next to MAX() SQLite fills x, y and z from the row it picked, the player's last position here
                    chunks.add(
                        new ChunkHits(
                            rs.getInt("dimensionID"),
                            rs.getInt("cx"),
                            rs.getInt("cz"),
                            rs.getLong("hits"),
                            rs.getLong("lastSeen"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z")));
                }
            }
        } catch (SQLException e) {
            TemporaQueries.throwIfTimedOut();
            throw e;
        }

        return pickHotSpots(chunks, count);
    }

    // Picks the busiest places from the time spent in each chunk, see the class comment
    public static List<HotSpot> pickHotSpots(List<ChunkHits> chunks, int count) {
        long total = 0;
        Map<Integer, Map<Long, ChunkHits>> byDimension = new HashMap<>();
        for (ChunkHits chunk : chunks) {
            total += chunk.hits;
            byDimension.computeIfAbsent(chunk.dimension, d -> new HashMap<>())
                .put(key(chunk.chunkX, chunk.chunkZ), chunk);
        }
        if (total == 0) return new ArrayList<>();

        // Every chunk as the middle of a place, with the time spent in the chunks around it
        List<HotSpot> candidates = new ArrayList<>();
        for (ChunkHits chunk : chunks) {
            Map<Long, ChunkHits> dimension = byDimension.get(chunk.dimension);
            long hits = 0;
            ChunkHits latest = chunk;
            for (int dx = -AREA_RADIUS; dx <= AREA_RADIUS; dx++) {
                for (int dz = -AREA_RADIUS; dz <= AREA_RADIUS; dz++) {
                    ChunkHits near = dimension.get(key(chunk.chunkX + dx, chunk.chunkZ + dz));
                    if (near == null) continue;
                    hits += near.hits;
                    if (near.lastSeen > latest.lastSeen) latest = near;
                }
            }
            candidates.add(new HotSpot(chunk, hits, (double) hits / total, latest));
        }

        // Ties go to the place seen last then by position, so the same history always gives the same answer
        candidates.sort(
            Comparator.comparingLong((HotSpot spot) -> spot.hits)
                .thenComparingLong(spot -> spot.lastSeen)
                .reversed()
                .thenComparingInt(spot -> spot.dimension)
                .thenComparingInt(spot -> spot.chunkX)
                .thenComparingInt(spot -> spot.chunkZ));

        List<HotSpot> picked = new ArrayList<>();
        for (HotSpot candidate : candidates) {
            if (picked.size() == count || candidate.share < MIN_SHARE) break;
            if (picked.stream()
                .noneMatch(spot -> spot.isNear(candidate))) picked.add(candidate);
        }
        return picked;
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    // E.g. "1. 52% in Overworld at [x y z], last there 2h ago"
    public static IChatComponent spotLine(int rank, HotSpot spot) {
        return EventChat.line(
            "tempora.command.home.spot",
            rank,
            EventChat.text(formatNumber(Math.round(spot.share * 100)) + "%", EnumChatFormatting.WHITE),
            EventChat.text(GenericUtils.getDimensionName(spot.dimension), EnumChatFormatting.WHITE),
            CommandUtils.teleportChatComponent(spot.x, spot.y, spot.z, spot.dimension, CommandUtils.TeleportType.EXACT),
            EventChat.time(spot.lastSeen));
    }

    // How many of a player's positions were logged in one chunk, and their last one there
    public static final class ChunkHits {

        final int dimension, chunkX, chunkZ;
        final long hits, lastSeen;
        final double x, y, z;

        public ChunkHits(int dimension, int chunkX, int chunkZ, long hits, long lastSeen, double x, double y,
            double z) {
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.hits = hits;
            this.lastSeen = lastSeen;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    // A place a player spent time, the chunks around chunkX, chunkZ. x, y, z is where they last stood there, which is
    // safe to teleport to unlike the middle of the chunk
    public static final class HotSpot {

        public final int dimension, chunkX, chunkZ;
        public final long hits, lastSeen;
        public final double share, x, y, z;

        HotSpot(ChunkHits middle, long hits, double share, ChunkHits latest) {
            this.dimension = middle.dimension;
            this.chunkX = middle.chunkX;
            this.chunkZ = middle.chunkZ;
            this.hits = hits;
            this.share = share;
            this.lastSeen = latest.lastSeen;
            this.x = latest.x;
            this.y = latest.y;
            this.z = latest.z;
        }

        boolean isNear(HotSpot other) {
            return dimension == other.dimension && Math.abs(chunkX - other.chunkX) <= SEPARATION
                && Math.abs(chunkZ - other.chunkZ) <= SEPARATION;
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
        suggestions.put("t", Arrays.asList("1d", "1w", "3w", "1mo"));
        suggestions.put("dim", dimensions);
        suggestions.put("n", Arrays.asList("1", "3", "5", "10"));
        return CommandFilters.complete(args, suggestions);
    }

    @Override
    protected Object[] getDescriptionArgs() {
        int placeChunks = AREA_RADIUS * 2 + 1;
        return new Object[] { placeChunks, placeChunks };
    }

    @Override
    protected Object[] getArgumentArgs(int index) {
        if (index == 3) return new Object[] { MAX_COUNT, DEFAULT_COUNT };
        return super.getArgumentArgs(index);
    }

    @Override
    public String getExampleArgs() {
        return "Notch t:3w n:3";
    }
}
