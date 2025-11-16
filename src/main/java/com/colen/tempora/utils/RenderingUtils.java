package com.colen.tempora.utils;

import com.colen.tempora.Tempora;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.rendering.RenderUtils;
import com.colen.tempora.utils.nbt.NBTUtils;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import java.util.Objects;

import static com.colen.tempora.utils.nbt.NBTUtils.NBT_DISABLED;
import static com.colen.tempora.utils.nbt.NBTUtils.NO_NBT;

public class RenderingUtils {

    public static void renderBlockWithLogging(RenderWorldLastEvent e, GenericQueueElement queueElement, int blockID, int metadata, String encodedNBT, String playerUUID, LoggerEnum loggerType) {
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
            FMLLog.warning(
                "[Tempora] Failed to render %s event (eventID=%s) at (%.1f, %.1f, %.1f) in dim %d. "
                    + "BlockID=%d:%d Player=%s NBT=%s Timestamp=%d | Exception: %s: %s",
                loggerType,
                queueElement.eventID,
                queueElement.x,
                queueElement.y,
                queueElement.z,
                queueElement.dimensionId,
                blockID,
                metadata, // Block ID + metadata
                playerUUID, // Player UUID
                (encodedNBT != null && !encodedNBT.isEmpty()
                    ? encodedNBT.substring(0, Math.min(encodedNBT.length(), 64)) + "..."
                    : "none"), // Safe truncated NBT preview
                queueElement.timestamp,
                exception.getClass().getSimpleName(), // Exception type
                exception.getMessage() // Exception message
            );

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
