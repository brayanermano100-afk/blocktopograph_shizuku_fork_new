package com.mithrilmania.blocktopograph.chunk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Parses the "Data3D" chunk record introduced in 1.18 ("Caves & Cliffs Part
 * II"), which replaced the old flat Data2D biome array with a proper 3D one
 * so caves can have different biomes than the surface above them.
 * <p>
 * Layout: a 16x16 int16 heightmap (512 bytes, skipped here -- we don't need
 * it for biome lookups) followed by 24 paletted 16x16x16 biome-id layers,
 * stacked bottom-to-top from subchunk index -4 (world Y -64) to index 19
 * (world Y 319).
 * <p>
 * Each layer uses the exact same palette bit-packing scheme Mojang already
 * uses for block storage (see V1d2d13TerrainSubChunk's BlockStorage class in
 * this codebase) -- just with plain int32 biome ids in the palette instead
 * of NBT block-state compounds.
 * <p>
 * NOTE: this was written from documentation/community reverse-engineering
 * notes, not verified byte-for-byte against a real 1.18+ world yet. If
 * biomes come out looking wrong/shifted, that's the place to double check.
 */
final class Biome3DData {

    private static final int HEIGHTMAP_BYTES = 512; // 16 * 16 * 2 bytes
    private static final int LAYER_COUNT = 24;
    static final int MIN_Y = -64;

    // One resolved (already palette-looked-up) biome id per block, per layer.
    // null entry = that layer failed to parse / wasn't present.
    private final int[][] layerBiomes = new int[LAYER_COUNT][];
    private boolean usable = false;

    Biome3DData(byte[] raw) {
        try {
            if (raw == null || raw.length <= HEIGHTMAP_BYTES) return;
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.position(HEIGHTMAP_BYTES);

            for (int layer = 0; layer < LAYER_COUNT; layer++) {
                if (buffer.remaining() < 1) break; // short data, stop gracefully
                int header = buffer.get() & 0xff;
                int bitsPerEntry = header >> 1;

                int[] resolved = new int[4096];

                if (bitsPerEntry == 0) {
                    // Whole 16x16x16 layer is a single biome -- no packed index array.
                    if (buffer.remaining() < 4) break;
                    int paletteCount = buffer.getInt();
                    int single = 0;
                    if (paletteCount > 0 && buffer.remaining() >= 4) single = buffer.getInt();
                    for (int i = 1; i < paletteCount && buffer.remaining() >= 4; i++) buffer.getInt();
                    Arrays.fill(resolved, single);
                } else {
                    if (bitsPerEntry > 32) break;
                    int perWord = 32 / bitsPerEntry;
                    int wordCount = (4095 / perWord) + 1;
                    int byteLen = wordCount * 4;
                    if (buffer.remaining() < byteLen) break;
                    int[] words = new int[wordCount];
                    for (int i = 0; i < wordCount; i++) words[i] = buffer.getInt();

                    if (buffer.remaining() < 4) break;
                    int paletteCount = buffer.getInt();
                    int[] palette = new int[Math.max(paletteCount, 1)];
                    for (int i = 0; i < paletteCount; i++) {
                        if (buffer.remaining() < 4) break;
                        palette[i] = buffer.getInt();
                    }

                    int mask = (bitsPerEntry >= 32) ? -1 : ((1 << bitsPerEntry) - 1);
                    for (int i = 0; i < 4096; i++) {
                        int word = words[i / perWord];
                        int shift = (i % perWord) * bitsPerEntry;
                        int idx = (word >> shift) & mask;
                        resolved[i] = (idx >= 0 && idx < paletteCount) ? palette[idx] : 0;
                    }
                }

                layerBiomes[layer] = resolved;
            }
            usable = true;
        } catch (Exception ignored) {
            usable = false;
        }
    }

    boolean isUsable() {
        return usable;
    }

    /** x,z local to the chunk (0..15); worldY is the absolute world Y (-64..319). */
    int getBiome(int x, int worldY, int z) {
        int layer = (worldY - MIN_Y) >> 4;
        if (layer < 0) layer = 0;
        if (layer >= LAYER_COUNT) layer = LAYER_COUNT - 1;
        int[] data = layerBiomes[layer];
        if (data == null) return 0;
        int localY = (worldY - MIN_Y) & 0xf;
        // Same x,z,y -> flat index convention as block storage: (((x<<4)|z)<<4)|y
        int offset = (((x << 4) | z) << 4) | localY;
        return data[offset];
    }
}
