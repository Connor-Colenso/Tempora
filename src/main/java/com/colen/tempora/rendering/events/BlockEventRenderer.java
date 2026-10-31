package com.colen.tempora.rendering.events;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.utils.nbt.NBTUtils.NBT_DISABLED;
import static com.colen.tempora.utils.nbt.NBTUtils.NO_NBT;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

import com.colen.tempora.Tempora;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.nbt.NBTUtils;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws each event as a block, e.g. the block that was broken, outlined in a blinking colour. A block that cannot be
 * drawn shows as Tempora's rendering error block instead.
 */
@SideOnly(Side.CLIENT)
public abstract class BlockEventRenderer<E extends PositionalEventInfo> implements EventRenderer<E> {

    private final Color highlight;

    // Made once per event rather than every frame, as decoding NBT and building a tile entity is not cheap. Weak, so
    // each goes once ClientEventStore drops its event.
    private final Map<E, GhostBlock> ghosts = new WeakHashMap<>();

    protected BlockEventRenderer(Color highlight) {
        this.highlight = highlight;
    }

    protected abstract int blockID(E event);

    protected abstract int metadata(E event);

    /** The block's tile entity NBT as NBTUtils encodes it, or NBTUtils.NO_NBT or NBTUtils.NBT_DISABLED. */
    protected abstract String encodedNBT(E event);

    @Override
    public void renderWorld(List<E> events, EventRenderContext context) {
        // Several changes to one block would draw on top of each other, so only the newest is shown.
        for (E event : EventRenderHelpers.latestPerBlock(events)) {
            GhostBlock ghost = ghosts.get(event);
            if (ghost == null) {
                ghost = createGhost(event);
                ghosts.put(event, ghost);
            }

            try {
                ghost.render(event, highlight, context);
            } catch (RuntimeException | LinkageError e) {
                logFailure(event, e);
                ghosts.put(event, GhostBlock.ERROR);
            }
        }
    }

    private GhostBlock createGhost(E event) {
        try {
            Block block = Block.getBlockById(blockID(event));
            int metadata = metadata(event);
            return new GhostBlock(block, metadata, tileEntityFor(block, metadata, encodedNBT(event)));
        } catch (RuntimeException | LinkageError e) {
            logFailure(event, e);
            return GhostBlock.ERROR;
        }
    }

    private static @Nullable TileEntity tileEntityFor(Block block, int metadata, @Nullable String encodedNBT) {
        TileEntity tileEntity;
        if (encodedNBT == null || NO_NBT.equals(encodedNBT) || NBT_DISABLED.equals(encodedNBT)) {
            tileEntity = defaultTileEntity(block, metadata);
        } else {
            // Null for tile entities this client does not have.
            tileEntity = TileEntity.createAndLoadEntity(NBTUtils.decodeFromString(encodedNBT));
        }
        if (tileEntity == null) return null;

        tileEntity.blockMetadata = metadata;
        tileEntity.blockType = block;
        tileEntity.setWorldObj(Minecraft.getMinecraft().theWorld);
        // It is drawn at the origin.
        tileEntity.xCoord = 0;
        tileEntity.yCoord = 0;
        tileEntity.zCoord = 0;
        tileEntity.validate();
        return tileEntity;
    }

    // Without its NBT, a block with a tile entity still gets a fresh one, as some renderers (AE2 crafting units) draw
    // nothing without it. One that cannot be made without a real world is left out, as before.
    private static @Nullable TileEntity defaultTileEntity(Block block, int metadata) {
        if (!block.hasTileEntity(metadata)) return null;
        try {
            return block.createTileEntity(Minecraft.getMinecraft().theWorld, metadata);
        } catch (RuntimeException e) {
            LOG.warn(
                "Could not make a tile entity for {}:{} to draw it with, so it is drawn without one.",
                Block.blockRegistry.getNameForObject(block),
                metadata,
                e);
            return null;
        }
    }

    // Once per event, as the event then shows as the error block rather than failing every frame.
    private void logFailure(E event, Throwable e) {
        String encodedNBT = encodedNBT(event);
        String truncatedNBT = encodedNBT != null && !encodedNBT.isEmpty()
            ? encodedNBT.substring(0, Math.min(encodedNBT.length(), 64)) + "..."
            : "none";

        LOG.warn(
            "Failed to render {} event (eventID={}) at ({}, {}, {}) in dim {}. BlockID={}:{} NBT={}. It is shown as an error block instead.",
            event.getLoggerName(),
            event.eventID,
            String.format("%.1f", event.x),
            String.format("%.1f", event.y),
            String.format("%.1f", event.z),
            event.dimensionID,
            blockID(event),
            metadata(event),
            truncatedNBT,
            e);
    }

    private static final class GhostBlock {

        static final GhostBlock ERROR = new GhostBlock(Tempora.renderingErrorBlock, 0, null);

        final Block block;
        final int metadata;
        final @Nullable TileEntity tileEntity;

        GhostBlock(Block block, int metadata, @Nullable TileEntity tileEntity) {
            this.block = block;
            this.metadata = metadata;
            this.tileEntity = tileEntity;
        }

        void render(PositionalEventInfo event, Color highlight, EventRenderContext context) {
            RenderUtils.renderBlockInWorld(
                event.x,
                event.y,
                event.z,
                block,
                metadata,
                tileEntity,
                highlight,
                context.partialTicks);
        }
    }
}
