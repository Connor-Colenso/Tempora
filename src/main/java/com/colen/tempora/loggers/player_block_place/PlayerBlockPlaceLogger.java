package com.colen.tempora.loggers.player_block_place;

import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.GenericUtils.isClientSide;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;
import static com.colen.tempora.utils.nbt.NBTUtils.getEncodedTileEntityNBT;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.BlockEvent.MultiPlaceEvent;
import net.minecraftforge.event.world.BlockEvent.PlaceEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerBlockPlaceLogger extends GenericPositionalLogger<PlayerBlockPlaceEventInfo> {

    @Override
    protected String playerColumn() {
        return "playerUUID";
    }

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
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        // GT miners, pumps etc post this as a fake player under the owner's name to ask if they can place a block,
        // before placing or without placing at all, so it names whatever block was already there. Only keep a player's
        // own placements, what machines actually change ends up in the block change logger
        if (event.player instanceof FakePlayer) return;

        // A door, bed or GT/mod multiblock item places several blocks at once, one snapshot each. The event's own
        // position is only the first
        if (event instanceof MultiPlaceEvent multi) {
            for (BlockSnapshot replaced : multi.getReplacedBlockSnapshots()) {
                logBlockPlace(
                    event.player,
                    event.world,
                    replaced.x,
                    replaced.y,
                    replaced.z,
                    event.world.getBlock(replaced.x, replaced.y, replaced.z));
            }
            return;
        }

        logBlockPlace(event.player, event.world, event.x, event.y, event.z, event.block);
    }

    // Logs a block being placed. Call after it's in the world so the meta and TE can be read. player can be null if
    // nobody in particular placed it
    public void logBlockPlace(@Nullable EntityPlayer player, World world, int x, int y, int z, Block block) {
        if (!isEnabledByConfig) return;
        // Forge reports every block an item use changed and some (AE2 cable parts) leave one as air, which was never
        // placed
        if (block == Blocks.air) return;

        PlayerBlockPlaceEventInfo eventInfo = new PlayerBlockPlaceEventInfo();
        eventInfo.blockID = Block.getIdFromBlock(block);
        eventInfo.metadata = world.getBlockMetadata(x, y, z);

        ItemStack pickStack = getPickBlockSafe(block, world, x, y, z);
        if (pickStack != null && pickStack.getItem() != null) {
            eventInfo.pickBlockID = Item.getIdFromItem(pickStack.getItem());
            eventInfo.pickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fall back to raw values if there's no pick block
            eventInfo.pickBlockID = eventInfo.blockID;
            eventInfo.pickBlockMeta = eventInfo.metadata;
        }

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
