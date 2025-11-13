package com.colen.tempora.utils;

import java.util.*;

import net.minecraft.world.ChunkPosition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;

public class ChunkPositionUtils {

    private static final String POS_SEPARATOR = ";";
    private static final String COORD_SEPARATOR = ",";

    /** Encode an entire collection of ChunkPositions into a single string */
    public static @NotNull String encodePositions(@NotNull Iterable<ChunkPosition> positions) {
        StringBuilder sb = new StringBuilder();
        for (ChunkPosition pos : positions) {
            if (sb.length() > 0) sb.append(POS_SEPARATOR);
            sb.append(pos.chunkPosX)
                .append(COORD_SEPARATOR)
                .append(pos.chunkPosY)
                .append(COORD_SEPARATOR)
                .append(pos.chunkPosZ);
        }
        return sb.toString();
    }

    /** Decode a string back into a HashSet of ChunkPositions */
    public static @NotNull HashSet<ChunkPosition> decodePositions(@Nullable String encoded) {
        HashSet<ChunkPosition> result = new HashSet<>();
        if (encoded == null || encoded.isEmpty()  || encoded.equals(MISSING_STRING_DATA)) return result;

        String[] parts = encoded.split(POS_SEPARATOR);
        for (String part : parts) {
            String[] coords = part.split(COORD_SEPARATOR, 3);
            if (coords.length != 3) continue; // skip malformed
            try {
                int x = Integer.parseInt(coords[0].trim());
                int y = Integer.parseInt(coords[1].trim());
                int z = Integer.parseInt(coords[2].trim());
                result.add(new ChunkPosition(x, y, z));
            } catch (NumberFormatException ignored) {}
        }

        return result;
    }
}
