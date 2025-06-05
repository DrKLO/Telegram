package com.carrotsearch.randomizedtesting;

/**
 * Hash routines for primitive types. The implementation is based on the finalization step
 * from Austin Appleby's <code>MurmurHash3</code>.
 *
 * @see "http://sites.google.com/site/murmurhash/"
 */
final class MurmurHash3
{
    private MurmurHash3()
    {
        // no instances.
    }

    /**
     * Hashes a 4-byte sequence (Java int).
     */
    public static int hash(int k)
    {
        k ^= k >>> 16;
        k *= 0x85ebca6b;
        k ^= k >>> 13;
        k *= 0xc2b2ae35;
        k ^= k >>> 16;
        return k;
    }

    /**
     * Hashes an 8-byte sequence (Java long).
     */
    public static long hash(long k)
    {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;

        return k;
    }
}