package com.mithrilmania.blocktopograph.chunkscan;

import androidx.annotation.Nullable;

import com.mithrilmania.blocktopograph.WorldActivity;
import com.mithrilmania.blocktopograph.map.Dimension;
import com.mithrilmania.blocktopograph.nbt.EditableNBT;
import com.mithrilmania.blocktopograph.nbt.tags.CompoundTag;
import com.mithrilmania.blocktopograph.nbt.tags.FloatTag;
import com.mithrilmania.blocktopograph.nbt.tags.IntTag;
import com.mithrilmania.blocktopograph.nbt.tags.ListTag;
import com.mithrilmania.blocktopograph.nbt.tags.Tag;

import java.util.ArrayList;
import java.util.List;

public final class ChunkScanUtil {

    private ChunkScanUtil() {
    }

    public static class PlayerLocation {
        public final int chunkX, chunkZ;
        public final Dimension dimension;

        PlayerLocation(int chunkX, int chunkZ, Dimension dimension) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
        }
    }

    /** Reads the local player's chunk position + dimension, or null if it can't be found. */
    @Nullable
    public static PlayerLocation getLocalPlayerLocation(WorldActivity activity) {
        try {
            EditableNBT nbt = activity.getEditablePlayer();
            CompoundTag player = null;
            for (Tag t : nbt.getTags()) {
                if (t instanceof CompoundTag) {
                    player = (CompoundTag) t;
                    break;
                }
            }
            if (player == null) return null;

            Tag posTag = player.getChildTagByKey("Pos");
            Tag dimTag = player.getChildTagByKey("DimensionId");
            if (!(posTag instanceof ListTag) || !(dimTag instanceof IntTag)) return null;

            List<Tag> pos = ((ListTag) posTag).getValue();
            float x = ((FloatTag) pos.get(0)).getValue();
            float z = ((FloatTag) pos.get(2)).getValue();
            Integer dimId = ((IntTag) dimTag).getValue();
            Dimension dimension = dimId == null ? Dimension.OVERWORLD : Dimension.getDimension(dimId);
            if (dimension == null) dimension = Dimension.OVERWORLD;

            return new PlayerLocation(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4, dimension);
        } catch (Exception e) {
            return null;
        }
    }

    /** All chunk (x,z) coordinates in a square of the given radius around a center chunk. */
    public static List<int[]> chunksAround(int centerChunkX, int centerChunkZ, int radius) {
        List<int[]> list = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                list.add(new int[]{centerChunkX + dx, centerChunkZ + dz});
            }
        }
        return list;
    }
}
