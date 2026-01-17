package com.colen.tempora.loggers.player_block_break;

import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.GenericUtils.isClientSide;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;
import static com.colen.tempora.utils.nbt.NBTUtils.NBT_DISABLED;
import static com.colen.tempora.utils.nbt.NBTUtils.NO_NBT;
import static com.colen.tempora.utils.nbt.NBTUtils.getEncodedTileEntityNBT;

import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.UndoResponse;
import com.colen.tempora.utils.RenderingUtils;
import com.colen.tempora.utils.nbt.NBTUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PlayerBlockBreakLogger extends GenericPositionalLogger<PlayerBlockBreakEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.PLAYER_BLOCK_BREAK;
    }

    @Override
    public @NotNull PlayerBlockBreakEventInfo newEventInfo() {
        return new PlayerBlockBreakEventInfo();
    }

    private static boolean logNBT;

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {
        List<PlayerBlockBreakEventInfo> sortedList = getSortedLatestEventsByDistance(
            transparentEventsToRenderInWorld,
            renderEvent);

        for (PlayerBlockBreakEventInfo pbbEventInfo : sortedList) {
            RenderingUtils.quickRenderBlockWithHighlightAndChecks(
                renderEvent,
                pbbEventInfo,
                pbbEventInfo.blockID,
                pbbEventInfo.metadata,
                pbbEventInfo.encodedNBT,
                pbbEventInfo.playerUUID,
                this);
        }

        for (PlayerBlockBreakEventInfo pbbEventInfo : nonTransparentEventsToRenderInWorld) {
            RenderingUtils.quickRenderBlockWithHighlightAndChecks(
                renderEvent,
                pbbEventInfo,
                pbbEventInfo.blockID,
                pbbEventInfo.metadata,
                pbbEventInfo.encodedNBT,
                pbbEventInfo.playerUUID,
                this);
        }
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        logNBT = config.getBoolean(
            "logNBT",
            getLoggerName(),
            true,
            """
                If true, it will log the NBT of all blocks changes which interact with this event. This improves rendering of events and gives a better history.
                WARNING: NBT may be large and this will cause the database to grow much quicker.
                """);
    }

    @Override
    public boolean isUndoEnabled() {
        return true;
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onBlockBreak(final @NotNull BlockEvent.BreakEvent event) {
        // Server side only.
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        PlayerBlockBreakEventInfo eventInfo = new PlayerBlockBreakEventInfo();
        eventInfo.eventID = UUID.randomUUID()
            .toString();
        eventInfo.x = event.x;
        eventInfo.y = event.y;
        eventInfo.z = event.z;
        eventInfo.dimensionID = event.world.provider.dimensionId;
        eventInfo.timestamp = System.currentTimeMillis();

        eventInfo.blockID = Block.getIdFromBlock(event.block);
        eventInfo.metadata = event.blockMetadata;

        eventInfo.encodedNBT = getEncodedTileEntityNBT(event.world, event.x, event.y, event.z, logNBT);

        // Calculate pickBlockID and pickBlockMeta using getPickBlock
        ItemStack pickStack = getPickBlockSafe(event.block, event.world, event.x, event.y, event.z);
        if (pickStack != null && pickStack.getItem() != null) {
            eventInfo.pickBlockID = Item.getIdFromItem(pickStack.getItem());
            eventInfo.pickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fallback to raw values if pickBlock is null
            eventInfo.pickBlockID = eventInfo.blockID;
            eventInfo.pickBlockMeta = eventInfo.metadata;
        }

        if (event.getPlayer() instanceof EntityPlayerMP) {
            eventInfo.playerUUID = event.getPlayer()
                .getUniqueID()
                .toString();
        } else {
            eventInfo.playerUUID = UNKNOWN_PLAYER_NAME;
        }

        queueEventInfo(eventInfo);
    }

    // Todo de-dupe code here and in other block adjacent loggers.
    // todo get rid of the need to cast the class here and use the generic.
    @Override
    public UndoResponse undoEvent(GenericEventInfo eventInfo, EntityPlayer player) {
        if (!(eventInfo instanceof PlayerBlockBreakEventInfo pbbEventInfo)) {
            UndoResponse undoResponse = new UndoResponse();
            undoResponse.message = new ChatComponentTranslation("tempora.undo.unknown_error", getLoggerName());
            undoResponse.success = false;
            return undoResponse;
        }

        // NBT existed but was not logged, it is not safe to undo this event.
        if (pbbEventInfo.encodedNBT.equals(NBT_DISABLED)) {
            UndoResponse undoResponse = new UndoResponse();
            undoResponse.message = new ChatComponentTranslation("tempora.undo.cannot_block_break.nbt_logging_disabled");
            undoResponse.success = false;
            return undoResponse;
        }

        World w = MinecraftServer.getServer()
            .worldServerForDimension(eventInfo.dimensionID);

        Block block = Block.getBlockById(pbbEventInfo.blockID);
        if (block == null) {
            UndoResponse undoResponse = new UndoResponse();
            undoResponse.message = new ChatComponentTranslation("tempora.undo.cannot_block_break.block_not_found");
            undoResponse.success = false;
            return undoResponse;
        }

        // Flag of 2 will update clients nearby.
        w.setBlock((int) pbbEventInfo.x, (int) pbbEventInfo.y, (int) pbbEventInfo.z, block, pbbEventInfo.metadata, 2);

        // Just to ensure meta is being set right, stops blocks interfering.
        w.setBlockMetadataWithNotify((int) pbbEventInfo.x, (int) pbbEventInfo.y, (int) pbbEventInfo.z, pbbEventInfo.metadata, 2);
        // Block had no NBT.
        if (pbbEventInfo.encodedNBT.equals(NO_NBT)) {
            UndoResponse undoResponse = new UndoResponse();
            undoResponse.message = new ChatComponentTranslation("tempora.undo.success.normal");
            undoResponse.success = true;
            return undoResponse;
        }

        try {
            TileEntity tileEntity = TileEntity.createAndLoadEntity(NBTUtils.decodeFromString(pbbEventInfo.encodedNBT));
            w.setTileEntity((int) pbbEventInfo.x, (int) pbbEventInfo.y, (int) pbbEventInfo.z, tileEntity);
        } catch (Exception e) {
            // Erase the block. Try to stop world state having issues.
            w.setBlockToAir((int) pbbEventInfo.x, (int) pbbEventInfo.y, (int) pbbEventInfo.z);
            w.removeTileEntity((int) pbbEventInfo.x, (int) pbbEventInfo.y, (int) pbbEventInfo.z);

            e.printStackTrace();
            UndoResponse undoResponse = new UndoResponse();
            undoResponse.message = new ChatComponentTranslation("tempora.undo.unknown_error", getLoggerName());
            undoResponse.success = false;
            return undoResponse;
        }

        UndoResponse undoResponse = new UndoResponse();
        undoResponse.message = new ChatComponentTranslation("tempora.undo.success.normal");
        undoResponse.success = true;
        return undoResponse;
    }
}
