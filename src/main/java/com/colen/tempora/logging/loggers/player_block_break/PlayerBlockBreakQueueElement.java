package com.colen.tempora.logging.loggers.player_block_break;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

public class PlayerBlockBreakQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public int pickBlockID;
    public int pickBlockMeta;
    public String playerUUIDWhoBrokeBlock;

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent block = BlockUtils.getUnlocalisedChatComponent(pickBlockID, pickBlockMeta);
        IChatComponent coords = generateTeleportChatComponent(x, y, z, CoordFormat.INT);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        return new ChatComponentTranslation(
            "message.block_break",
            PlayerUtils.UUIDToName(playerUUIDWhoBrokeBlock), // player name
            block, // block localized name
            blockID, // block ID
            metadata, // metadata
            coords, // clickable coordinates
            timeAgo // localized relative time
        );
    }
}
