package com.colen.tempora.loggers.block_change;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;
import static com.colen.tempora.utils.nbt.NBTUtils.NBT_DISABLED;
import static com.colen.tempora.utils.nbt.NBTUtils.NO_NBT;
import static com.colen.tempora.utils.nbt.NBTUtils.encodeToString;
import static com.colen.tempora.utils.nbt.NBTUtils.getEncodedTileEntityNBT;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.GenericUtils;
import com.colen.tempora.utils.StackTraceUtils;
import com.colen.tempora.utils.WorldGenPhaseTracker;
import com.colen.tempora.utils.WorldGenTicks;

public class BlockChangeLogger extends GenericPositionalLogger<BlockChangeEventInfo> {

    // Block changes don't know who did them, so the closest player stands in
    @Override
    protected String playerColumn() {
        return "closestPlayerUUID";
    }

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.BLOCK_CHANGE;
    }

    @Override
    public @NotNull String getShortName() {
        return "change";
    }

    // Every setBlock is way too much data to log everywhere, so by default only inside regions that turn it on
    @Override
    protected boolean defaultForLogByDefault() {
        return false;
    }

    public static boolean isLogNBTEnabled() {
        return logNBT;
    }

    private static boolean logNBT;

    @Override
    protected void addSettingsInfo(List<IChatComponent> lines) {
        super.addSettingsInfo(lines);
        lines.add(
            settingLine(logNBT ? "tempora.logger.setting.nbt_blocks.on" : "tempora.logger.setting.nbt_blocks.off"));
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        logNBT = config.getBoolean(
            "logNBT",
            getLoggerName(),
            false,
            """
                If true, it will log the NBT of all blocks changes which interact with this event. This improves rendering of events and gives a better history.
                WARNING: NBT may be large and this could cause the database to grow much quicker.
                """);
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.None;
    }

    @Override
    public @NotNull BlockChangeEventInfo newEventInfo() {
        return new BlockChangeEventInfo();
    }

    // Not convinced this needs to be thread safe
    private static final ThreadLocal<Deque<BlockChangeEventInfo>> BLOCK_STACK = ThreadLocal
        .withInitial(ArrayDeque::new);

    // Stack traces. Each distinct trace is stored once in this table next to the events with its own number. Events
    // point at it by that number (stackTraceID), a byte or two instead of eight for the hash. The hash
    // (StackTraceUtils.getID) finds a trace's row

    static final String TRACE_TABLE = "StackTraces";

    // Traces seen since the db opened that might not be stored yet, hash -> text. Written with the next batch
    private final Map<Long, String> unsavedTraces = new ConcurrentHashMap<>();
    // Hash -> number for traces known to be stored, so each gets looked up once per session. Only added once the batch
    // that stored it commits, a rolled back number can go to another trace
    private final Map<Long, Long> traceNumbers = new ConcurrentHashMap<>();

    // Hash to give an event for this trace, queues the trace to be written with it if new
    public long traceHash(String trace) {
        long hash = StackTraceUtils.getID(trace);
        if (!traceNumbers.containsKey(hash)) unsavedTraces.putIfAbsent(hash, trace);
        return hash;
    }

    @Override
    protected void createExtraTables(Connection conn) throws SQLException {
        // New db (another world in singleplayer) doesn't have any of them yet
        unsavedTraces.clear();
        traceNumbers.clear();
        try (Statement st = conn.createStatement()) {
            // AUTOINCREMENT so a pruned trace's number never gets reused
            st.execute(
                "CREATE TABLE IF NOT EXISTS " + TRACE_TABLE
                    + " (id INTEGER PRIMARY KEY AUTOINCREMENT, hash INTEGER NOT NULL UNIQUE, trace TEXT NOT NULL)");
            // For pruning, which checks if any event still points at each trace
            st.execute(
                "CREATE INDEX IF NOT EXISTS idx_" + getLoggerName()
                    + "_stackTraceID ON "
                    + getLoggerName()
                    + " (stackTraceID)");
        }
    }

    // Gives each event its trace number, storing new traces. In the batch transaction, before columns get read
    @Override
    protected @Nullable Runnable writeExtras(List<? extends GenericEventInfo> events, Connection conn)
        throws SQLException {
        Map<Long, Long> numbered = new HashMap<>();
        try (PreparedStatement find = conn.prepareStatement("SELECT id FROM " + TRACE_TABLE + " WHERE hash = ?");
            PreparedStatement insert = conn
                .prepareStatement("INSERT INTO " + TRACE_TABLE + " (hash, trace) VALUES (?, ?) RETURNING id")) {
            for (GenericEventInfo event : events) {
                if (!(event instanceof BlockChangeEventInfo change) || change.stackTraceHash == 0) continue;
                long hash = change.stackTraceHash;

                Long number = traceNumbers.get(hash);
                if (number == null) number = numbered.get(hash);
                if (number == null) {
                    number = findTraceNumber(find, hash);
                    if (number == null) {
                        String trace = unsavedTraces.get(hash);
                        // Its text went with the db it was seen in (singleplayer switched worlds)
                        number = trace == null ? 0 : insertTrace(insert, hash, trace);
                    }
                    numbered.put(hash, number);
                }
                change.stackTraceID = number;
            }
        }
        if (numbered.isEmpty()) return null;

        return () -> {
            for (Map.Entry<Long, Long> trace : numbered.entrySet()) {
                if (trace.getValue() == 0) continue;
                traceNumbers.put(trace.getKey(), trace.getValue());
                unsavedTraces.remove(trace.getKey());
            }
        };
    }

    private static @Nullable Long findTraceNumber(PreparedStatement find, long hash) throws SQLException {
        find.setLong(1, hash);
        try (ResultSet rs = find.executeQuery()) {
            return rs.next() ? rs.getLong(1) : null;
        }
    }

    private static long insertTrace(PreparedStatement insert, long hash, String trace) throws SQLException {
        insert.setLong(1, hash);
        insert.setString(2, trace);
        try (ResultSet rs = insert.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Override
    public void loadExtras(List<? extends GenericEventInfo> events, Connection conn) throws SQLException {
        Map<Long, List<BlockChangeEventInfo>> byTrace = new HashMap<>();
        for (GenericEventInfo event : events) {
            if (event instanceof BlockChangeEventInfo change && change.stackTraceID != 0) {
                byTrace.computeIfAbsent(change.stackTraceID, id -> new ArrayList<>())
                    .add(change);
            }
        }
        if (byTrace.isEmpty()) return;

        String placeholders = String.join(", ", Collections.nCopies(byTrace.size(), "?"));
        try (PreparedStatement ps = conn
            .prepareStatement("SELECT id, trace FROM " + TRACE_TABLE + " WHERE id IN (" + placeholders + ")")) {
            int i = 1;
            for (long id : byTrace.keySet()) ps.setLong(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    for (BlockChangeEventInfo change : byTrace.get(rs.getLong(1))) {
                        change.setStackTrace(rs.getString(2));
                    }
                }
            }
        }
    }

    // Deletes traces no stored event points at, except ones numbered since the db opened. An event pointing at one
    // might still be in the queue, and any number we've handed out has to stay stored
    @Override
    protected void pruneExtras(Connection conn) throws SQLException {
        List<Long> unreferenced = new ArrayList<>();
        try (Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(
                "SELECT id FROM " + TRACE_TABLE
                    + " WHERE NOT EXISTS (SELECT 1 FROM "
                    + getLoggerName()
                    + " WHERE stackTraceID = "
                    + TRACE_TABLE
                    + ".id)")) {
            while (rs.next()) unreferenced.add(rs.getLong(1));
        }

        Set<Long> inUse = new HashSet<>(traceNumbers.values());
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + TRACE_TABLE + " WHERE id = ?")) {
            for (long id : unreferenced) {
                if (inUse.contains(id)) continue;
                ps.setLong(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // Pushed instead of a real event for changes that won't be logged, so HEAD and RETURN stay paired without paying
    // for a stack trace and NBT we'd throw away
    private static final BlockChangeEventInfo SKIPPED = new BlockChangeEventInfo();

    // newBlock is the block being set, null for a meta change
    public void onSetBlockHead(int x, int y, int z, World world, @Nullable Block newBlock) {
        // Not isActive(), the worker can stop between HEAD and RETURN and unpair them. These two only change with the
        // config so both calls of a pair always agree
        if (!isEnabledByConfig || !GenericUtils.shouldTemporaRun()) return;

        // Pushed before anything that can throw, so this call's RETURN always pops its own entry and not the outer
        // setBlock's. Replaced below if the change gets logged
        Deque<BlockChangeEventInfo> stack = BLOCK_STACK.get();
        stack.push(SKIPPED);
        int dimensionID = world.provider.dimensionId;

        // TODO log world gen separately?
        if (WorldGenPhaseTracker.isWorldGen() || !canLogHere(x, y, z, dimensionID)) return;

        Block before = world.getBlock(x, y, z);
        if (WorldGenTicks.isWorldGenChange(world, x, y, z, before, newBlock == null ? before : newBlock)) return;

        BlockChangeEventInfo e = new BlockChangeEventInfo();
        stack.pop();
        stack.push(e);

        e.timestamp = System.currentTimeMillis();
        e.stackTraceHash = traceHash(StackTraceUtils.getCallingClassChain());

        e.x = x;
        e.y = y;
        e.z = z;
        e.dimensionID = dimensionID;

        // Before state
        e.beforeBlockID = Block.getIdFromBlock(before);
        e.beforeMetadata = world.getBlockMetadata(x, y, z);

        ItemStack pick = getPickBlockSafe(before, world, x, y, z);
        if (pick != null && pick.getItem() != null) {
            e.beforePickBlockID = Item.getIdFromItem(pick.getItem());
            e.beforePickBlockMeta = pick.getItemDamage();
        } else {
            e.beforePickBlockID = e.beforeBlockID;
            e.beforePickBlockMeta = e.beforeMetadata;
        }

        e.beforeEncodedNBT = getEncodedTileEntityNBT(world, x, y, z, BlockChangeLogger.isLogNBTEnabled());
    }

    public void onSetBlockReturn(int x, int y, int z, World world, CallbackInfoReturnable<Boolean> cir) {
        if (!isEnabledByConfig || !GenericUtils.shouldTemporaRun()) return;

        Deque<BlockChangeEventInfo> stack = BLOCK_STACK.get();

        if (stack.isEmpty()) {
            LOG.error("[BLOCK CHANGE LOGGER CRITICAL ERROR] RETURN without matching HEAD", new Exception());
            throw new IllegalStateException();
        }

        BlockChangeEventInfo e = stack.pop();

        // Not logged here, or placement failed
        if (e == SKIPPED || !cir.getReturnValue()) {
            return;
        }

        fillAfterState(e, world, x, y, z);
        recordSetBlock(x, y, z, e, world);
    }

    // Logs a block change that didn't go through Chunk.func_150807_a so the mixin never saw it, e.g. written straight
    // into chunk storage. Call after the change with what was there before (old block, meta and TE NBT if it had one)
    public void logBlockChange(World world, int x, int y, int z, Block before, int beforeMetadata,
        @Nullable NBTTagCompound beforeTileEntityNBT) {
        if (!isEnabledByConfig) return;

        int dimensionID = world.provider.dimensionId;
        if (WorldGenPhaseTracker.isWorldGen() || !canLogHere(x, y, z, dimensionID)) return;
        if (WorldGenTicks.isWorldGenChange(world, x, y, z, before, world.getBlock(x, y, z))) return;

        BlockChangeEventInfo e = new BlockChangeEventInfo();
        e.timestamp = System.currentTimeMillis();
        e.stackTraceHash = traceHash(StackTraceUtils.getCallingClassChain());

        e.x = x;
        e.y = y;
        e.z = z;
        e.dimensionID = dimensionID;

        // Before state as reported. The world already has the new block so the pick block can only come from the meta
        e.beforeBlockID = Block.getIdFromBlock(before);
        e.beforeMetadata = beforeMetadata;

        Item beforeItem = Item.getItemFromBlock(before);
        if (beforeItem != null) {
            e.beforePickBlockID = Item.getIdFromItem(beforeItem);
            e.beforePickBlockMeta = before.damageDropped(beforeMetadata);
        } else {
            e.beforePickBlockID = e.beforeBlockID;
            e.beforePickBlockMeta = e.beforeMetadata;
        }

        if (beforeTileEntityNBT == null) {
            e.beforeEncodedNBT = NO_NBT;
        } else if (logNBT) {
            e.beforeEncodedNBT = encodeToString(beforeTileEntityNBT);
        } else {
            e.beforeEncodedNBT = NBT_DISABLED;
        }

        fillAfterState(e, world, x, y, z);
        recordSetBlock(x, y, z, e, world);
    }

    private static void fillAfterState(BlockChangeEventInfo e, World world, int x, int y, int z) {
        Block after = world.getBlock(x, y, z);
        e.afterBlockID = Block.getIdFromBlock(after);
        e.afterMetadata = world.getBlockMetadata(x, y, z);

        ItemStack pick = getPickBlockSafe(after, world, x, y, z);
        if (pick != null && pick.getItem() != null) {
            e.afterPickBlockID = Item.getIdFromItem(pick.getItem());
            e.afterPickBlockMeta = pick.getItemDamage();
        } else {
            e.afterPickBlockID = e.afterBlockID;
            e.afterPickBlockMeta = e.afterMetadata;
        }

        e.afterEncodedNBT = getEncodedTileEntityNBT(world, x, y, z, BlockChangeLogger.isLogNBTEnabled());
    }

    private void recordSetBlock(int x, int y, int z, BlockChangeEventInfo eventInfo, World world) {
        EntityPlayer closestPlayer = world.getClosestPlayer(x, y, z, -1);
        double closestDistance = -1;

        if (closestPlayer != null) {
            closestDistance = Math.round(closestPlayer.getDistance(x, y, z));
        }

        eventInfo.closestPlayerUUID = closestPlayer != null ? closestPlayer.getUniqueID()
            .toString() : UNKNOWN_PLAYER_NAME;
        eventInfo.closestPlayerDistance = closestDistance;

        // Mods often set up a new block's TE right after placing it, GT gives a machine its type, owner and facing,
        // vanilla a skull its type. Until then it reads as a blank block, so anything with a TE gets read again at the
        // end of the tick
        if (Block.getBlockById(eventInfo.afterBlockID)
            .hasTileEntity(eventInfo.afterMetadata)) {
            pendingChanges.add(new PendingChange(eventInfo, world, x, y, z));
        } else {
            queueEventInfo(eventInfo);
        }
    }

    // Changes waiting for the end of the tick

    private final Queue<PendingChange> pendingChanges = new ConcurrentLinkedQueue<>();

    private static final class PendingChange {

        final BlockChangeEventInfo event;
        final World world;
        final int x, y, z;

        PendingChange(BlockChangeEventInfo event, World world, int x, int y, int z) {
            this.event = event;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    // Reads blocks with a TE that changed this tick again, now whoever placed them has set them up, and queues their
    // events. End of every server tick and on server stop
    public void onTickEnd() {
        PendingChange change;
        while ((change = pendingChanges.poll()) != null) {
            try {
                rereadAfterState(change);
            } catch (RuntimeException | LinkageError e) {
                // Another mod's block or TE, the event keeps what was read when it changed
                LOG.error("Tempora could not read a changed block again at the end of the tick.", e);
            }
            queueEventInfo(change.event);
        }
    }

    private static void rereadAfterState(PendingChange change) {
        BlockChangeEventInfo e = change.event;
        World world = change.world;
        int x = change.x, y = change.y, z = change.z;

        // Skip if the world or chunk has unloaded since, reading would load it again
        if (DimensionManager.getWorld(e.dimensionID) != world || !world.blockExists(x, y, z)) return;
        // Only while it's still the block this change made. If it changed again this tick that's its own event, and
        // this one keeps what was read at the time
        if (Block.getIdFromBlock(world.getBlock(x, y, z)) != e.afterBlockID
            || world.getBlockMetadata(x, y, z) != e.afterMetadata) return;

        fillAfterState(e, world, x, y, z);
    }

    @Override
    protected void beforeServerClose() {
        onTickEnd();
    }

}
