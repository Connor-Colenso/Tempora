package com.colen.tempora.loggers.player_block_break;

import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.GenericUtils.isClientSide;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;
import static com.colen.tempora.utils.nbt.NBTUtils.getEncodedTileEntityNBT;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlayerBlockBreakLogger extends GenericPositionalLogger<PlayerBlockBreakEventInfo> {

    // The player who broke the block.
    @Override
    protected String playerColumn() {
        return "playerUUID";
    }

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.PLAYER_BLOCK_BREAK;
    }

    @Override
    public @NotNull String getShortName() {
        return "break";
    }

    @Override
    public @NotNull PlayerBlockBreakEventInfo newEventInfo() {
        return new PlayerBlockBreakEventInfo();
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
            true,
            """
                If true, it will log the NBT of all blocks changes which interact with this event. This improves rendering of events and gives a better history.
                WARNING: NBT may be large and this will cause the database to grow much quicker.
                """);
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onBlockBreak(final @NotNull BlockEvent.BreakEvent event) {
        // Server side only.
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        // Machines post this event to ask whether they may break a block, often without breaking it: GT miners and
        // pumps as a fake player under their owner's name, and an AE2 annihilation plane every few seconds for the air
        // or liquid in front of it, as the player who placed it. Only a player's own breaks are kept, and a player
        // never breaks air or a liquid. What machines really change is in the change logger.
        if (event.getPlayer() instanceof FakePlayer) return;
        if (event.block.isAir(event.world, event.x, event.y, event.z) || event.block.getMaterial()
            .isLiquid()) return;

        logBlockBreak(event.getPlayer(), event.world, event.x, event.y, event.z, event.block, event.blockMetadata);
    }

    /**
     * Logs a block being broken. Call this before the block is removed, so its tile entity's NBT can still be read.
     * {@code player} may be null when nobody in particular broke it.
     */
    public void logBlockBreak(@Nullable EntityPlayer player, World world, int x, int y, int z, Block block,
        int metadata) {
        if (!isEnabledByConfig) return;

        PlayerBlockBreakEventInfo eventInfo = new PlayerBlockBreakEventInfo();
        eventInfo.blockID = Block.getIdFromBlock(block);
        eventInfo.metadata = metadata;

        eventInfo.encodedNBT = getEncodedTileEntityNBT(world, x, y, z, logNBT);

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

        if (player instanceof EntityPlayerMP) {
            eventInfo.playerUUID = player.getUniqueID()
                .toString();
        } else {
            eventInfo.playerUUID = UNKNOWN_PLAYER_NAME;
        }

        logEvent(eventInfo, world.provider.dimensionId, x, y, z);
    }
}
