package com.colen.tempora.utils;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.utils.nbt.NBTUtils.NBT_DISABLED;
import static com.colen.tempora.utils.nbt.NBTUtils.NO_NBT;

import java.util.Objects;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import com.colen.tempora.Tempora;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.nbt.NBTUtils;

public class RenderingUtils {

    public static int CLIENT_EVENT_RENDER_DISTANCE;

    public static void quickRenderBlockWithHighlightAndChecks(RenderWorldLastEvent e, GenericQueueElement queueElement,
        int blockID, int metadata, String encodedNBT, String playerUUID, LoggerEnum loggerType) {
        try {
            NBTTagCompound nbt = null;
            if (!Objects.equals(encodedNBT, NO_NBT) && !Objects.equals(encodedNBT, NBT_DISABLED)) {
                nbt = NBTUtils.decodeFromString(encodedNBT);
            }

            RenderUtils.renderBlockInWorld(
                e,
                queueElement.x,
                queueElement.y,
                queueElement.z,
                blockID,
                metadata,
                nbt,
                loggerType);
        } catch (Exception exception) {
            // Render an error block here instead, if something went critically wrong.
            String truncatedNBT = (encodedNBT != null && !encodedNBT.isEmpty()
                ? encodedNBT.substring(0, Math.min(encodedNBT.length(), 64)) + "..."
                : "none");

            String sx = String.format("%.1f", queueElement.x);
            String sy = String.format("%.1f", queueElement.y);
            String sz = String.format("%.1f", queueElement.z);

            LOG.warn(
                "Failed to render {} event (eventID={}) at ({}, {}, {}) in dim {}. BlockID={}:{} Player={} NBT={} Timestamp={} | Exception: {}: {}",
                loggerType,
                queueElement.eventID,
                sx,
                sy,
                sz,
                queueElement.dimensionId,
                blockID,
                metadata,
                playerUUID,
                truncatedNBT,
                queueElement.timestamp,
                exception.getClass()
                    .getSimpleName(),
                exception.getMessage());

            // Optionally print full stack trace to console for devs
            exception.printStackTrace();

            RenderUtils.renderBlockInWorld(
                e,
                queueElement.x,
                queueElement.y,
                queueElement.z,
                Block.getIdFromBlock(Tempora.renderingErrorBlock),
                0,
                null,
                loggerType);
        }
    }
}
