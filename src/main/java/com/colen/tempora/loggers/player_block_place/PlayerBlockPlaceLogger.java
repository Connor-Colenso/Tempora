package com.colen.tempora.loggers.player_block_place;

import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.GenericUtils.isClientSide;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;
import static com.colen.tempora.utils.nbt.NBTUtils.getEncodedTileEntityNBT;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.BlockEvent.PlaceEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerBlockPlaceLogger extends GenericPositionalLogger<PlayerBlockPlaceEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.PLAYER_BLOCK_PLACE;
    }

    @Override
    public @NotNull String getShortName() {
        return "place";
    }

    @Override
    public @NotNull PlayerBlockPlaceEventInfo newEventInfo() {
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
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onBlockPlace(final @NotNull PlaceEvent event) {
        if (!isEnabledByConfig) return;
        if (isClientSide()) return; // Server side only
        if (event.isCanceled()) return;

        logBlockPlace(event.player, event.world, event.x, event.y, event.z, event.block);
    }

    /**
     * Logs a block being placed. Call this after it is in the world, so its metadata and tile entity can be read.
     * {@code player} may be null when nobody in particular placed it.
     */
    public void logBlockPlace(@Nullable EntityPlayer player, World world, int x, int y, int z, Block block) {
        if (!isEnabledByConfig) return;

        PlayerBlockPlaceEventInfo eventInfo = new PlayerBlockPlaceEventInfo();
        eventInfo.blockID = Block.getIdFromBlock(block);
        eventInfo.metadata = world.getBlockMetadata(x, y, z);

        // Calculate pickBlockID and pickBlockMeta using getPickBlock
        ItemStack pickStack = getPickBlockSafe(block, world, x, y, z);
        if (pickStack != null && pickStack.getItem() != null) {
            eventInfo.pickBlockID = Item.getIdFromItem(pickStack.getItem());
            eventInfo.pickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fallback to raw values if pickBlock is null
            eventInfo.pickBlockID = eventInfo.blockID;
            eventInfo.pickBlockMeta = eventInfo.metadata;
        }

        // Log NBT.
        eventInfo.encodedNBT = getEncodedTileEntityNBT(world, x, y, z, logNBT);

        if (player instanceof EntityPlayerMP) {
            eventInfo.playerUUID = player.getUniqueID()
                .toString();
        } else {
            eventInfo.playerUUID = UNKNOWN_PLAYER_NAME;
        }

        logEvent(eventInfo, world.provider.dimensionId, x, y, z);
    }

}
