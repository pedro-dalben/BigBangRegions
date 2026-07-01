package com.bigbangcraft.regions.allocation;

public final class BiomeCoordinateMath {
    private static final int QUART_XZ_BITS = 24;
    private static final int QUART_Y_BITS = 16;
    private static final long QUART_XZ_MASK = (1L << QUART_XZ_BITS) - 1L;
    private static final long QUART_Y_MASK = (1L << QUART_Y_BITS) - 1L;
    private static final int QUART_XZ_BIAS = 1 << (QUART_XZ_BITS - 1);
    private static final int QUART_Y_BIAS = 1 << (QUART_Y_BITS - 1);

    private BiomeCoordinateMath() {
    }

    public static int blockToQuart(int block) {
        return Math.floorDiv(block, 4);
    }

    public static int quartToBlock(int quart) {
        return quart * 4;
    }

    public static long packQuart(int quartX, int quartY, int quartZ) {
        return (encodeSigned(quartX, QUART_XZ_BITS) << (QUART_Y_BITS + QUART_XZ_BITS))
            | (encodeSigned(quartY, QUART_Y_BITS) << QUART_XZ_BITS)
            | encodeSigned(quartZ, QUART_XZ_BITS);
    }

    public static int unpackQuartX(long packed) {
        return decodeSigned((packed >>> (QUART_Y_BITS + QUART_XZ_BITS)) & QUART_XZ_MASK, QUART_XZ_BITS);
    }

    public static int unpackQuartY(long packed) {
        return decodeSigned((packed >>> QUART_XZ_BITS) & QUART_Y_MASK, QUART_Y_BITS);
    }

    public static int unpackQuartZ(long packed) {
        return decodeSigned(packed & QUART_XZ_MASK, QUART_XZ_BITS);
    }

    private static long encodeSigned(int value, int bits) {
        long bias = 1L << (bits - 1);
        long min = -bias;
        long max = bias - 1L;
        if (value < min || value > max) {
            throw new IllegalArgumentException("Quart coordinate out of supported range for packing: " + value);
        }
        return value + bias;
    }

    private static int decodeSigned(long packed, int bits) {
        long bias = 1L << (bits - 1);
        return (int)(packed - bias);
    }
}
