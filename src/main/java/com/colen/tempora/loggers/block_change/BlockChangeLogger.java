package com.colen.tempora.loggers.block_change;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.TemporaUtils.UNKNOWN_PLAYER_NAME;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;
import static com.colen.tempora.utils.RenderingUtils.CLIENT_EVENT_RENDER_DISTANCE;
import static com.colen.tempora.utils.nbt.NBTUtils.NBT_DISABLED;
import static com.colen.tempora.utils.nbt.NBTUtils.NO_NBT;
import static com.colen.tempora.utils.nbt.NBTUtils.getEncodedTileEntityNBT;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.loggers.optional.ISupportsUndo;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.DatabaseUtils;
import com.colen.tempora.utils.GenericUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.RenderingUtils;
import com.colen.tempora.utils.WorldGenPhaseTracker;
import com.colen.tempora.utils.nbt.NBTUtils;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// todo look into logging world gen and marking that separately, such that you can use a useful regen command to restore
// the state of the world.
public class BlockChangeLogger extends GenericPositionalLogger<BlockChangeQueueElement> implements ISupportsUndo {

    private boolean globalBlockChangeLogging;

    public static boolean isLogNBTEnabled() {
        return logNBT;
    }

    private static boolean logNBT;

    /**
     * Track nested block writes per thread.
     * We only log when depth goes from 0 -> 1 -> 0.
     */
    private static final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

    private static final ThreadLocal<SetBlockEventInfo> currentEvent = new ThreadLocal<>();

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.BlockChangeLogger;
    }

    @SideOnly(Side.CLIENT)
    ArrayList<BlockChangeQueueElement> filteredNonTransparentBuffer = new ArrayList<>();

    @SideOnly(Side.CLIENT)
    ArrayList<BlockChangeQueueElement> filteredTransparentBuffer = new ArrayList<>();

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;

        if (player == null) return;

        double maxDistSq = CLIENT_EVENT_RENDER_DISTANCE * CLIENT_EVENT_RENDER_DISTANCE;

        // Clear and pre-size buffers
        filteredNonTransparentBuffer.clear();
        filteredTransparentBuffer.clear();

        filteredNonTransparentBuffer.ensureCapacity(nonTransparentEventsToRenderInWorld.size());
        filteredTransparentBuffer.ensureCapacity(transparentEventsToRenderInWorld.size());

        // --- NON-TRANSPARENT ---
        for (BlockChangeQueueElement event : nonTransparentEventsToRenderInWorld) {
            if (event.dimensionId != player.dimension) continue;
            if (player.getDistanceSq(event.x, event.y, event.z) > maxDistSq) continue;
            filteredNonTransparentBuffer.add(event);
        }

        for (BlockChangeQueueElement bcqe : filteredNonTransparentBuffer) {
            RenderingUtils.quickRenderBlockWithHighlightAndChecks(
                e,
                bcqe,
                bcqe.beforeBlockID,
                bcqe.beforeMetadata,
                bcqe.beforeEncodedNBT,
                bcqe.closestPlayerUUID,
                getLoggerType());
        }

        // --- TRANSPARENT ---
        for (BlockChangeQueueElement event : transparentEventsToRenderInWorld) {
            if (event.dimensionId != player.dimension) continue;
            if (player.getDistanceSq(event.x, event.y, event.z) > maxDistSq) continue;
            filteredTransparentBuffer.add(event);
        }

        for (BlockChangeQueueElement bcqe : getSortedLatestEventsByDistance(filteredTransparentBuffer, e)) {
            RenderingUtils.quickRenderBlockWithHighlightAndChecks(
                e,
                bcqe,
                bcqe.beforeBlockID,
                bcqe.beforeMetadata,
                bcqe.beforeEncodedNBT,
                bcqe.closestPlayerUUID,
                getLoggerType());
        }
    }

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("beforeBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("beforeMetadata", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("beforePickBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("beforePickBlockMeta", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("beforeEncodedNBT", "TEXT", "NOT NULL DEFAULT " + NO_NBT),

            new ColumnDef("afterBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("afterMetadata", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("afterPickBlockID", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("afterPickBlockMeta", "INTEGER", "NOT NULL DEFAULT -1"),
            new ColumnDef("afterEncodedNBT", "TEXT", "NOT NULL DEFAULT " + NO_NBT),

            new ColumnDef("stackTrace", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("closestPlayerUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("closestPlayerDistance", "REAL", "NOT NULL DEFAULT -1"));
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        globalBlockChangeLogging = config.getBoolean("globalBlockChangeLogging", getLoggerName(), false, """
            If true, overrides all custom regions and logs every setBlock call across the entire world.
            WARNING: This will generate an enormous number of events and rapidly bloat your database.
            """);

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
    public void threadedSaveEvents(List<BlockChangeQueueElement> queueElements) throws SQLException {
        if (queueElements == null || queueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getLoggerName()
            + " (beforeBlockID, beforeMetadata, beforePickBlockID, beforePickBlockMeta, beforeEncodedNBT, afterBlockID, afterMetadata, afterPickBlockID, afterPickBlockMeta, afterEncodedNBT, stackTrace, closestPlayerUUID, closestPlayerDistance, eventID, x, y, z, dimensionID, timestamp, versionID) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int index;
        try (PreparedStatement pstmt = db.getDBConn()
            .prepareStatement(sql)) {
            for (BlockChangeQueueElement queueElement : queueElements) {
                index = 1;

                pstmt.setInt(index++, queueElement.beforeBlockID);
                pstmt.setInt(index++, queueElement.beforeMetadata);
                pstmt.setInt(index++, queueElement.beforePickBlockID);
                pstmt.setInt(index++, queueElement.beforePickBlockMeta);
                pstmt.setString(index++, queueElement.beforeEncodedNBT);

                pstmt.setInt(index++, queueElement.afterBlockID);
                pstmt.setInt(index++, queueElement.afterMetadata);
                pstmt.setInt(index++, queueElement.afterPickBlockID);
                pstmt.setInt(index++, queueElement.afterPickBlockMeta);
                pstmt.setString(index++, queueElement.afterEncodedNBT);

                pstmt.setString(index++, queueElement.stackTrace);
                pstmt.setString(index++, queueElement.closestPlayerUUID);
                pstmt.setDouble(index++, queueElement.closestPlayerDistance);

                DatabaseUtils.defaultColumnEntries(queueElement, pstmt, index);

                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.None;
    }

    @Override
    public @NotNull List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> events = new ArrayList<>();
        while (resultSet.next()) {
            BlockChangeQueueElement queueElement = new BlockChangeQueueElement();
            queueElement.populateDefaultFieldsFromResultSet(resultSet);

            queueElement.beforeBlockID = resultSet.getInt("beforeBlockID");
            queueElement.beforeMetadata = resultSet.getInt("beforeMetadata");
            queueElement.beforePickBlockID = resultSet.getInt("beforePickBlockID");
            queueElement.beforePickBlockMeta = resultSet.getInt("beforePickBlockMeta");
            queueElement.beforeEncodedNBT = resultSet.getString("beforeEncodedNBT");

            queueElement.afterBlockID = resultSet.getInt("afterBlockID");
            queueElement.afterMetadata = resultSet.getInt("afterMetadata");
            queueElement.afterPickBlockID = resultSet.getInt("afterPickBlockID");
            queueElement.afterPickBlockMeta = resultSet.getInt("afterPickBlockMeta");
            queueElement.afterEncodedNBT = resultSet.getString("afterEncodedNBT");

            queueElement.stackTrace = resultSet.getString("stackTrace");
            queueElement.closestPlayerUUID = PlayerUtils.UUIDToName(resultSet.getString("closestPlayerUUID"));
            queueElement.closestPlayerDistance = resultSet.getDouble("closestPlayerDistance");

            events.add(queueElement);
        }
        return events;
    }

    // Not convinced multithreaded safety is needed here.
    private static final ThreadLocal<Deque<BlockChangeQueueElement>> BLOCK_STACK = ThreadLocal
        .withInitial(ArrayDeque::new);

    public void onSetBlockHead(int x, int y, int z, World world) {

        Deque<BlockChangeQueueElement> stack = BLOCK_STACK.get();

        BlockChangeQueueElement e = new BlockChangeQueueElement();
        e.eventID = UUID.randomUUID()
            .toString();
        e.timestamp = System.currentTimeMillis();
        e.stackTrace = GenericUtils.getCallingClassChain();
        stack.push(e);

        // GenericQueueElement fields
        e.x = x;
        e.y = y;
        e.z = z;
        e.dimensionId = world.provider.dimensionId;

        e.isWorldGen = WorldGenPhaseTracker.isWorldGen();

        // BEFORE state
        Block before = world.getBlock(x, y, z);
        e.beforeBlockID = Block.getIdFromBlock(before);
        e.beforeMetadata = world.getBlockMetadata(x, y, z);

        ItemStack pick = before.getPickBlock(null, world, x, y, z);
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

        Deque<BlockChangeQueueElement> stack = BLOCK_STACK.get();

        if (stack.isEmpty()) {
            LOG.error("[BLOCK CHANGE LOGGER CRITICAL ERROR] RETURN without matching HEAD", new Exception());
            return;
        }

        BlockChangeQueueElement e = stack.pop();

        // If placement failed, mark as no-op and move on
        if (!cir.getReturnValue()) {
            return;
        }

        // AFTER state
        Block after = world.getBlock(x, y, z);
        e.afterBlockID = Block.getIdFromBlock(after);
        e.afterMetadata = world.getBlockMetadata(x, y, z);

        ItemStack pick = after.getPickBlock(null, world, x, y, z);
        if (pick != null && pick.getItem() != null) {
            e.afterPickBlockID = Item.getIdFromItem(pick.getItem());
            e.afterPickBlockMeta = pick.getItemDamage();
        } else {
            e.afterPickBlockID = e.afterBlockID;
            e.afterPickBlockMeta = e.afterMetadata;
        }

        e.afterEncodedNBT = getEncodedTileEntityNBT(world, x, y, z, BlockChangeLogger.isLogNBTEnabled());

        recordSetBlock(e.x, e.y, e.z, e, world);
    }

    private void recordSetBlock(double x, double y, double z, BlockChangeQueueElement queueElement, World world) {

        // Only log changes if (x, y, z) is inside a defined region. Unless config has entire world logging on.
        if (!globalBlockChangeLogging
            && !RegionRegistry.containsBlock(world.provider.dimensionId, (int) x, (int) y, (int) z)) {
            return;
        }

        EntityPlayer closestPlayer = world.getClosestPlayer(x, y, z, -1);
        double closestDistance = -1;

        if (closestPlayer != null) {
            closestDistance = closestPlayer.getDistance(x, y, z);
        }

        queueElement.closestPlayerUUID = closestPlayer != null ? closestPlayer.getUniqueID()
            .toString() : UNKNOWN_PLAYER_NAME;
        queueElement.closestPlayerDistance = closestDistance;

        queueEvent(queueElement);
    }

    @Override
    public IChatComponent undoEvent(GenericQueueElement queueElement) {
        if (!(queueElement instanceof BlockChangeQueueElement)) return new ChatComponentTranslation("error");

        BlockChangeQueueElement bcqe = (BlockChangeQueueElement) queueElement;

        if (bcqe.beforeEncodedNBT.equals(NBT_DISABLED))
            return new ChatComponentTranslation("tempora.cannot.block.break.undo.nbt.logging.disabled");

        World w = MinecraftServer.getServer()
            .worldServerForDimension(queueElement.dimensionId);
        Block block = Block.getBlockById(bcqe.beforeBlockID);
        if (block == null) return new ChatComponentTranslation("tempora.cannot.block.break.undo.block.not.found");

        int x = (int) bcqe.x;
        int y = (int) bcqe.y;
        int z = (int) bcqe.z;
        int meta = bcqe.beforeMetadata;

        // Place silently (no physics or callbacks)
        BlockUtils.setBlockNoUpdate(w, x, y, z, block, meta); // todo use this in other undos.

        if (!bcqe.beforeEncodedNBT.equals(NO_NBT)) {
            try {
                TileEntity te = TileEntity.createAndLoadEntity(NBTUtils.decodeFromString(bcqe.beforeEncodedNBT));
                if (te != null) {
                    te.setWorldObj(w);
                    te.xCoord = x;
                    te.yCoord = y;
                    te.zCoord = z;
                    te.validate();
                    w.setTileEntity(x, y, z, te);
                }
            } catch (Exception e) {
                w.setBlockToAir(x, y, z);
                w.removeTileEntity(x, y, z);
                e.printStackTrace();
                return new ChatComponentTranslation("tempora.undo.block.break.unknown.error");
            }
        }

        // Client visual + light refresh now that TE is correct
        w.markBlockForUpdate(x, y, z);

        return new ChatComponentTranslation("tempora.undo.success");
    }
}
