package com.colen.tempora.loggers.player_block_place;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.nbt.NBTUtils.getEncodedTileEntityNBT;

import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.BlockEvent.PlaceEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.TemporaUtils;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.RenderingUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PlayerBlockPlaceLogger extends GenericPositionalLogger<PlayerBlockPlaceEventInfo> {

    @Override
    public String getLoggerName() {
        return TemporaEvents.PLAYER_BLOCK_PLACE;
    }

    @Override
    public @NotNull PlayerBlockPlaceEventInfo getEventInfoInstance() {
        return new PlayerBlockPlaceEventInfo();
    }

    private boolean logNBT;

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        logNBT = config.getBoolean(
            "logNBT",
            getLoggerName(),
            true,
            """
                If true, it will log the NBT of all blocks changes which interact with this event. This improves rendering of events and gives a better history.
                WARNING: NBT may be large and this could cause the database to grow much quicker.
                """);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {
        List<PlayerBlockPlaceEventInfo> sortedList = getSortedLatestEventsByDistance(
            transparentEventsToRenderInWorld,
            renderEvent);

        for (PlayerBlockPlaceEventInfo pbbqe : sortedList) {
            RenderingUtils.quickRenderBlockWithHighlightAndChecks(
                renderEvent,
                pbbqe,
                pbbqe.blockID,
                pbbqe.metadata,
                pbbqe.encodedNBT,
                pbbqe.playerUUID,
                this);
        }

        for (PlayerBlockPlaceEventInfo pbbqe : nonTransparentEventsToRenderInWorld) {
            RenderingUtils.quickRenderBlockWithHighlightAndChecks(
                renderEvent,
                pbbqe,
                pbbqe.blockID,
                pbbqe.metadata,
                pbbqe.encodedNBT,
                pbbqe.playerUUID,
                this);
        }
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onBlockPlace(final @NotNull PlaceEvent event) {
        if (isClientSide()) return; // Server side only
        if (event.isCanceled()) return;

        PlayerBlockPlaceEventInfo eventInfo = new PlayerBlockPlaceEventInfo();
        eventInfo.eventID = UUID.randomUUID()
            .toString();
        eventInfo.x = event.x;
        eventInfo.y = event.y;
        eventInfo.z = event.z;
        eventInfo.dimensionID = event.world.provider.dimensionId;
        eventInfo.timestamp = System.currentTimeMillis();

        eventInfo.blockID = Block.getIdFromBlock(event.block);
        eventInfo.metadata = event.world.getBlockMetadata(event.x, event.y, event.z);

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

        // Log NBT.
        eventInfo.encodedNBT = getEncodedTileEntityNBT(event.world, event.x, event.y, event.z, logNBT);

        if (event.player instanceof EntityPlayerMP) {
            eventInfo.playerUUID = event.player.getUniqueID()
                .toString();
        } else {
            eventInfo.playerUUID = TemporaUtils.UNKNOWN_PLAYER_NAME;
        }

        queueEventInfo(eventInfo);
    }

}
