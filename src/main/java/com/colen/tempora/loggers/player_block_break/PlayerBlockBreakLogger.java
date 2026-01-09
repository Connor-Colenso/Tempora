package com.colen.tempora.loggers.player_block_break;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
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
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.RenderingUtils;
import com.colen.tempora.utils.nbt.NBTUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PlayerBlockBreakLogger extends GenericPositionalLogger<PlayerBlockBreakQueueElement> {

    @Override
    public @NotNull PlayerBlockBreakQueueElement getQueueElementInstance() {
        return new PlayerBlockBreakQueueElement();
    }

    private static boolean logNBT;

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {
        List<PlayerBlockBreakQueueElement> sortedList = getSortedLatestEventsByDistance(
            transparentEventsToRenderInWorld,
            renderEvent);

        for (PlayerBlockBreakQueueElement pbbqe : sortedList) {
            RenderingUtils.quickRenderBlockWithHighlightAndChecks(
                renderEvent,
                pbbqe,
                pbbqe.blockID,
                pbbqe.metadata,
                pbbqe.encodedNBT,
                pbbqe.playerUUID,
                this);
        }

        for (PlayerBlockBreakQueueElement pbbqe : nonTransparentEventsToRenderInWorld) {
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

        PlayerBlockBreakQueueElement queueElement = new PlayerBlockBreakQueueElement();
        queueElement.eventID = UUID.randomUUID()
            .toString();
        queueElement.x = event.x;
        queueElement.y = event.y;
        queueElement.z = event.z;
        queueElement.dimensionID = event.world.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.blockID = Block.getIdFromBlock(event.block);
        queueElement.metadata = event.blockMetadata;

        queueElement.encodedNBT = getEncodedTileEntityNBT(event.world, event.x, event.y, event.z, logNBT);

        // Calculate pickBlockID and pickBlockMeta using getPickBlock
        ItemStack pickStack = getPickBlockSafe(event.block, event.world, event.x, event.y, event.z);
        if (pickStack != null && pickStack.getItem() != null) {
            queueElement.pickBlockID = Item.getIdFromItem(pickStack.getItem());
            queueElement.pickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fallback to raw values if pickBlock is null
            queueElement.pickBlockID = queueElement.blockID;
            queueElement.pickBlockMeta = queueElement.metadata;
        }

        if (event.getPlayer() instanceof EntityPlayerMP) {
            queueElement.playerUUID = event.getPlayer()
                .getUniqueID()
                .toString();
        } else {
            queueElement.playerUUID = TemporaUtils.UNKNOWN_PLAYER_NAME;
        }

        queueEvent(queueElement);
    }

    // Todo de-dupe code here and in other block adjacent loggers.
    // todo get rid of the need to cast the class here and use the generic.
    @Override
    public IChatComponent undoEvent(GenericQueueElement queueElement, EntityPlayer player) {
        if (!(queueElement instanceof PlayerBlockBreakQueueElement pbbqe))
            return new ChatComponentTranslation("tempora.undo.unknown.error", getLoggerName());

        // NBT existed but was not logged, it is not safe to undo this event.
        if (pbbqe.encodedNBT.equals(NBT_DISABLED))
            return new ChatComponentTranslation("tempora.cannot.block.break.undo.nbt.logging.disabled");

        World w = MinecraftServer.getServer()
            .worldServerForDimension(queueElement.dimensionID);

        Block block = Block.getBlockById(pbbqe.blockID);
        if (block == null) return new ChatComponentTranslation("tempora.cannot.block.break.undo.block.not.found");

        // Flag of 2 will update clients nearby.
        w.setBlock((int) pbbqe.x, (int) pbbqe.y, (int) pbbqe.z, block, pbbqe.metadata, 2);

        // Just to ensure meta is being set right, stops blocks interfering.
        w.setBlockMetadataWithNotify((int) pbbqe.x, (int) pbbqe.y, (int) pbbqe.z, pbbqe.metadata, 2);
        // Block had no NBT.
        if (pbbqe.encodedNBT.equals(NO_NBT)) return new ChatComponentTranslation("tempora.undo.success");

        try {
            TileEntity tileEntity = TileEntity.createAndLoadEntity(NBTUtils.decodeFromString(pbbqe.encodedNBT));
            w.setTileEntity((int) pbbqe.x, (int) pbbqe.y, (int) pbbqe.z, tileEntity);
        } catch (Exception e) {
            // Erase the block. Try stop world state having issues.
            w.setBlockToAir((int) pbbqe.x, (int) pbbqe.y, (int) pbbqe.z);
            w.removeTileEntity((int) pbbqe.x, (int) pbbqe.y, (int) pbbqe.z);

            e.printStackTrace();
            return new ChatComponentTranslation("tempora.undo.unknown.error", getLoggerName());
        }

        return new ChatComponentTranslation("tempora.undo.success");
    }
}
