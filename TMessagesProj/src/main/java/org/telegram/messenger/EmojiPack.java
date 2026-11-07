package org.telegram.messenger;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * EPK3 version 2/3 reader. getEmoji returns a complete ready-to-draw ARGB_8888 Bitmap.
 * Palette, masks and predictor records are private storage details.
 * Indexed dependencies always reference independent roots (no recursive chains).
 * All decoding and the bounded root cache are protected by this instance's monitor.
 */
public final class EmojiPack {
    private static final String ASSET_NAME = "emoji.pack";
    private static final int NO_MASK = 0xFFFF;
    private static final int HEADER_SIZE = 32;
    private static final int ENTRY_SIZE = 20;
    private static final int SIDE = 64;
    private static final int PIXELS = SIDE * SIDE;
    private static final int ROOT_CACHE_SIZE = 8;

    private static final int DEFLATE = 0;
    private static final int INDEX_WEBP = 1;
    private static final int RGBA_WEBP = 2;
    private static final int FULL_IMAGE = 3;
    private static final int MAP_REFERENCE = 4;

    private final MappedByteBuffer buffer;
    private final int count;
    private final int emojiCount;
    private final Inflater inflater = new Inflater(true);
    private final BitmapFactory.Options options = new BitmapFactory.Options();
    private byte[] encoded = new byte[4096];
    private final byte[] decoded = new byte[PIXELS + 512];
    private final byte[] indices = new byte[PIXELS];
    private final int[] pixels = new int[PIXELS];
    private final int[] emojiPixels = new int[PIXELS];
    private final int[] palette = new int[256];
    private final byte[] paletteBytes = new byte[1024];
    private final int[] rootIds = new int[ROOT_CACHE_SIZE];
    private final long[] rootAges = new long[ROOT_CACHE_SIZE];
    private final byte[][] rootMaps = new byte[ROOT_CACHE_SIZE][];
    private long age;

    private static final class Holder {
        static final EmojiPack INSTANCE = open();
        private static EmojiPack open() {
            try {
                return new EmojiPack();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to open emoji.pack", e);
            }
        }
    }

    public static EmojiPack getInstance() {
        return Holder.INSTANCE;
    }

    private EmojiPack() throws IOException {
        // The mapping remains valid after closing the descriptors.
        try (AssetFileDescriptor afd = ApplicationLoader.applicationContext
                .getAssets().openFd(ASSET_NAME);
             FileInputStream stream = afd.createInputStream()) {
            long length = afd.getLength();
            if (length < HEADER_SIZE || length > Integer.MAX_VALUE) {
                throw new IOException("Invalid emoji pack length: " + length);
            }
            buffer = stream.getChannel().map(FileChannel.MapMode.READ_ONLY,
                    afd.getStartOffset(), length);
        }
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.getInt(0) != 0x334B5045 || (u16(4) != 2 && u16(4) != 3) || u16(6) != ENTRY_SIZE
                || buffer.getInt(8) != SIDE || buffer.getInt(12) != SIDE
                || buffer.getInt(24) != buffer.capacity() || buffer.getInt(28) != 0) {
            throw new IOException("Unsupported or damaged EPK3 header");
        }
        count = buffer.getInt(16);
        emojiCount = buffer.getInt(20);
        if (count < 1 || count > 65535 || emojiCount < 0 || emojiCount > count
                || HEADER_SIZE + (long) count * ENTRY_SIZE > buffer.capacity()) {
            throw new IOException("Invalid EPK3 record count");
        }
        validateMetadata();
        Arrays.fill(rootIds, -1);
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inPremultiplied = true;
    }

    private int u16(int offset) {
        return buffer.getShort(offset) & 0xFFFF;
    }

    private int u8(int offset) {
        return buffer.get(offset) & 0xFF;
    }

    private static int entry(int record) {
        return HEADER_SIZE + record * ENTRY_SIZE;
    }

    private int find(int key, int first, int end) {
        int low = first;
        int high = end - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int value = u16(entry(mid));
            if (value < key) low = mid + 1;
            else if (value > key) high = mid - 1;
            else return mid;
        }
        return -1;
    }

    private int findEmoji(int x, int y) {
        if (x < 0 || x > 15 || y < 0 || y >= 4096) return -1;
        return find(x * 4096 + y, 0, emojiCount);
    }

    private void validateMetadata() throws IOException {
        int dataStart = HEADER_SIZE + count * ENTRY_SIZE;
        for (int i = 0; i < count; i++) {
            int e = entry(i);
            if (i != 0 && i != emojiCount && u16(e) <= u16(e - ENTRY_SIZE)) {
                throw new IOException("Unsorted EPK3 IDs");
            }
            int mask = u16(e + 2);
            if ((i >= emojiCount && mask != NO_MASK)
                    || (i < emojiCount && mask != NO_MASK
                    && find(mask, emojiCount, count) < 0)) {
                throw new IOException("Invalid mask reference at record " + i);
            }
            int offset = buffer.getInt(e + 4);
            int length = u16(e + 8);
            int a = u16(e + 10);
            int b = u16(e + 12);
            int colors = u16(e + 14);
            int codec = u8(e + 16);
            int prediction = u8(e + 17);
            int channels = u8(e + 18) & 127;
            if (offset < dataStart || length == 0
                    || (long) offset + length > buffer.capacity()
                    || codec > MAP_REFERENCE || prediction > 6) {
                throw new IOException("Invalid EPK3 record " + i);
            }
            if ((codec == RGBA_WEBP || codec == FULL_IMAGE)) {
                if (colors != 0 || u8(e + 18) != 0 || prediction != 0 || (u8(e + 19) & 127) != 0
                        || a != NO_MASK || b != NO_MASK) {
                    throw new IOException("Invalid full image record " + i);
                }
                continue;
            }
            if (colors < 1 || colors > 256 || channels == 2 || channels > 4
                    || (channels == 0 && colors != 256)
                    || (codec == MAP_REFERENCE ? paletteLength(e) != length : paletteLength(e) >= length)
                    || (channels == 0 && ((u8(e + 19) & 1) != 0 || (u8(e + 18) & 128) != 0))) {
                throw new IOException("Invalid palette at record " + i);
            }
            if ((codec == MAP_REFERENCE) != (prediction == 4)) {
                throw new IOException("Invalid map reference encoding");
            }
            if (codec == MAP_REFERENCE && (a >= count || colors != u16(entry(a) + 14))) {
                throw new IOException("Map reference palette size mismatch");
            }
            if (prediction == 0) {
                if (a != NO_MASK || b != NO_MASK) {
                    throw new IOException("Unexpected root reference");
                }
            } else {
                validateRoot(a, i);
                if (prediction == 3 || prediction == 5 || prediction == 6) validateRoot(b, i);
                else if (b != NO_MASK) throw new IOException("Unexpected second root");
            }
        }
    }

    private void validateRoot(int root, int child) throws IOException {
        if (root >= count || root == child) {
            throw new IOException("Invalid root reference");
        }
        int e = entry(root);
        if (u8(e + 16) > INDEX_WEBP || u8(e + 17) != 0
                || u16(e + 10) != NO_MASK || u16(e + 12) != NO_MASK) {
            throw new IOException("EPK3 dependency chains are forbidden");
        }
    }

    /** Returns a complete, premultiplied, ready-to-draw emoji owned by the caller. */
    public synchronized Bitmap getEmoji(int x, int y) {
        int record = findEmoji(x, y);
        if (record < 0) return null;
        int maskId = u16(entry(record) + 2);
        if (maskId == NO_MASK) return decode(record);
        decodePixels(record, emojiPixels);
        decodePixels(find(maskId, emojiCount, count), pixels);
        for (int i = 0; i < PIXELS; i++) {
            int color = emojiPixels[i];
            int alpha = ((color >>> 24) * (pixels[i] & 255) + 127) / 255;
            emojiPixels[i] = (color & 0x00FFFFFF) | (alpha << 24);
        }
        return Bitmap.createBitmap(emojiPixels, SIDE, SIDE, Bitmap.Config.ARGB_8888);
    }

    /** Clears the internal decode cache. Returned emoji Bitmaps remain owned by the caller. */
    public synchronized void clearCache() {
        Arrays.fill(rootIds, -1);
        Arrays.fill(rootMaps, null);
        Arrays.fill(rootAges, 0L);
        age = 0;
    }

    private Bitmap decode(int record) {
        int e = entry(record);
        int offset = buffer.getInt(e + 4);
        int length = u16(e + 8);
        int codec = u8(e + 16);
        if ((codec == RGBA_WEBP || codec == FULL_IMAGE)) {
            readBytes(offset, length);
            return codec == RGBA_WEBP ? decodeWebp(20, length) : decodeBitmap(20, length);
        }
        decodePixels(record, pixels);
        return Bitmap.createBitmap(pixels, SIDE, SIDE, Bitmap.Config.ARGB_8888);
    }

    private void decodePixels(int record, int[] output) {
        int e = entry(record);
        int offset = buffer.getInt(e + 4);
        int length = u16(e + 8);
        int codec = u8(e + 16);
        if ((codec == RGBA_WEBP || codec == FULL_IMAGE)) {
            readBytes(offset, length);
            options.inPremultiplied = false;
            Bitmap bitmap = null;
            try {
                bitmap = codec == RGBA_WEBP ? decodeWebp(20, length) : decodeBitmap(20, length);
                bitmap.getPixels(output, 0, SIDE, 0, 0, SIDE, SIDE);
            } finally {
                options.inPremultiplied = true;
                if (bitmap != null) bitmap.recycle();
            }
            return;
        }
        decodeIndices(record, indices);
        int colors = u16(e + 14);
        int channels = u8(e + 18) & 127;
        int components = colors * channels;
        boolean compressedPalette = (u8(e + 18) & 128) != 0;
        if (compressedPalette) {
            int compressedLength = paletteLength(e) - 2;
            readBytes(offset + 2, compressedLength);
            int expected = (u8(e + 19) & 1) != 0 ? (components * 7 + 7) / 8 : components;
            inflate(compressedLength, expected);
        }
        if ((u8(e + 19) & 1) != 0) {
            for (int i = 0; i < components; i++) {
                int bit = i * 7;
                int shift = bit & 7;
                int pos = bit >>> 3;
                int v = (compressedPalette ? decoded[pos] & 255 : u8(offset + pos)) >>> shift;
                if (shift > 1) v |= (compressedPalette ? decoded[pos + 1] & 255 : u8(offset + pos + 1)) << (8 - shift);
                v &= 127;
                paletteBytes[i] = (byte) ((v << 1) | (v >>> 6));
            }
        } else {
            if (compressedPalette) System.arraycopy(decoded, 0, paletteBytes, 0, components);
            else for (int i = 0; i < components; i++) paletteBytes[i] = buffer.get(offset + i);
        }
        int cursor = 0;
        for (int i = 0; i < colors; i++) {
            if (channels == 0) {
                palette[i] = 0xFF000000 | i * 0x010101;
            } else if (channels == 1) {
                palette[i] = 0xFF000000 | (paletteBytes[cursor++] & 255) * 0x010101;
            } else {
                int r = paletteBytes[cursor++] & 255;
                int g = paletteBytes[cursor++] & 255;
                int b = paletteBytes[cursor++] & 255;
                int a = channels == 4 ? paletteBytes[cursor++] & 255 : 255;
                palette[i] = a << 24 | r << 16 | g << 8 | b;
            }
        }
        for (int i = 0; i < PIXELS; i++) {
            int index = indices[i] & 255;
            if (index >= colors) throw damaged("Palette index out of range");
            output[i] = palette[index];
        }
    }

    private byte[] rootMap(int record) {
        int slot = 0;
        for (int i = 0; i < ROOT_CACHE_SIZE; i++) {
            if (rootIds[i] == record) {
                rootAges[i] = ++age;
                return rootMaps[i];
            }
            if (rootAges[i] < rootAges[slot]) slot = i;
        }
        if (rootMaps[slot] == null) rootMaps[slot] = new byte[PIXELS];
        // Mark invalid first: a failed decode must not leave a partially overwritten cache hit.
        rootIds[slot] = -1;
        decodeIndices(record, rootMaps[slot]);
        rootIds[slot] = record;
        rootAges[slot] = ++age;
        return rootMaps[slot];
    }

    private void decodeIndices(int record, byte[] result) {
        int e = entry(record);
        int prediction = u8(e + 17);
        byte[] a = null;
        byte[] b = null;
        int aColors = 0;
        int bColors = 0;
        if (prediction != 0) {
            int aId = u16(e + 10);
            a = rootMap(aId);
            aColors = u16(entry(aId) + 14);
            if (prediction == 3 || prediction == 5 || prediction == 6) {
                int bId = u16(e + 12);
                b = rootMap(bId);
                bColors = u16(entry(bId) + 14);
            }
        }
        int aTransform = (u8(e + 19) >>> 1) & 7;
        int bTransform = (u8(e + 19) >>> 4) & 7;
        if (prediction == 4) {
            for (int i = 0; i < PIXELS; i++) result[i] = a[sourceIndex(i, aTransform)];
            return;
        }
        int lutLength = aColors + bColors;
        int paletteLength = paletteLength(e);
        int offset = buffer.getInt(e + 4) + paletteLength;
        int length = u16(e + 8) - paletteLength;
        int cut = 32;
        if (prediction == 5 || prediction == 6) {
            if (length < 2) throw damaged("Truncated split record");
            cut = u8(offset++);
            length--;
            if (cut < 1 || cut >= SIDE) throw damaged("Invalid split position");
        }
        readBytes(offset, length);
        byte[] lut;
        if (u8(e + 16) == DEFLATE) {
            inflate(length, PIXELS + lutLength);
            lut = decoded;
            System.arraycopy(decoded, lutLength, result, 0, PIXELS);
        } else {
            if (length <= lutLength) throw damaged("Truncated index WebP");
            System.arraycopy(encoded, 20, decoded, 0, lutLength);
            Bitmap bitmap = decodeWebp(20 + lutLength, length - lutLength);
            try {
                bitmap.getPixels(pixels, 0, SIDE, 0, 0, SIDE, SIDE);
                for (int i = 0; i < PIXELS; i++) result[i] = (byte) pixels[i];
            } finally {
                bitmap.recycle();
            }
            lut = decoded;
        }
        if (prediction == 1 || prediction == 2) {
            for (int i = 0; i < PIXELS; i++) {
                int index = a[sourceIndex(i, aTransform)] & 255;
                if (index >= aColors) throw damaged("Root index out of range");
                int p = lut[index] & 255;
                result[i] = prediction == 1
                        ? (byte) ((result[i] & 255) ^ p)
                        : (byte) ((result[i] & 255) + p);
            }
        } else if (prediction == 3 || prediction == 5 || prediction == 6) {
            for (int i = 0; i < PIXELS; i++) {
                boolean left = prediction == 6 ? (i >>> 6) < cut : (i & 63) < cut;
                int index = (left ? a[sourceIndex(i, aTransform)]
                        : b[sourceIndex(i, bTransform)]) & 255;
                if (index >= (left ? aColors : bColors)) {
                    throw damaged("Half root index out of range");
                }
                int p = lut[(left ? 0 : aColors) + index] & 255;
                result[i] = (byte) ((result[i] & 255) + p);
            }
        }
        int colors = u16(e + 14);
        if (colors < 256) {
            for (byte value : result) {
                if ((value & 255) >= colors) throw damaged("Decoded index out of range");
            }
        }
    }

    private static int sourceIndex(int i, int transform) {
        int x = i & 63;
        int y = i >>> 6;
        if ((transform & 1) != 0) x = 63 - x;
        if ((transform & 2) != 0) y = 63 - y;
        return (transform & 4) != 0 ? (x << 6) | y : (y << 6) | x;
    }

    private void readBytes(int offset, int length) {
        if (encoded.length < length + 21) {
            int size = encoded.length;
            while (size < length + 21) size <<= 1;
            encoded = new byte[size];
        }
        // Only this small record is copied. The pack stays mapped and is never read in full.
        buffer.position(offset);
        buffer.get(encoded, 20, length);
    }

    private int paletteLength(int e) {
        if ((u8(e + 18) & 128) != 0) {
            if (u16(e + 8) < 3) throw damaged("Truncated compressed palette");
            int length = u16(buffer.getInt(e + 4));
            if (length == 0 || length + 2 > u16(e + 8)) throw damaged("Invalid compressed palette");
            return length + 2;
        }
        int n = u16(e + 14) * (u8(e + 18) & 127);
        return (u8(e + 19) & 1) != 0 ? (n * 7 + 7) / 8 : n;
    }

    private void putInt(int offset, int value) {
        encoded[offset] = (byte) value;
        encoded[offset + 1] = (byte) (value >>> 8);
        encoded[offset + 2] = (byte) (value >>> 16);
        encoded[offset + 3] = (byte) (value >>> 24);
    }

    private Bitmap decodeWebp(int offset, int length) {
        // Restore the standard 20-byte RIFF/VP8L wrapper omitted from the pack.
        int start = offset - 20;
        int pad = length & 1;
        putInt(start, 0x46464952); // RIFF
        putInt(start + 4, 12 + length + pad);
        putInt(start + 8, 0x50424557); // WEBP
        putInt(start + 12, 0x4C385056); // VP8L
        putInt(start + 16, length);
        if (pad != 0) encoded[offset + length] = 0;
        return decodeBitmap(start, 20 + length + pad);
    }

    private Bitmap decodeBitmap(int offset, int length) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(encoded, offset, length, options);
        if (bitmap == null) throw damaged("Bitmap decoder rejected image");
        if (bitmap.getWidth() != SIDE || bitmap.getHeight() != SIDE) {
            bitmap.recycle();
            throw damaged("Unexpected bitmap dimensions");
        }
        return bitmap;
    }

    private void inflate(int length, int expected) {
        inflater.reset();
        inflater.setInput(encoded, 20, length);
        try {
            int total = 0;
            while (total < expected) {
                int n = inflater.inflate(decoded, total, expected - total);
                if (n == 0) throw damaged("Truncated DEFLATE stream");
                total += n;
            }
            // Consume a possible final end marker without accepting extra output.
            if (!inflater.finished()) {
                int n = inflater.inflate(indices, 0, 1);
                if (n != 0 || !inflater.finished()) throw damaged("Oversized DEFLATE stream");
            }
            if (inflater.getRemaining() != 0) throw damaged("Trailing DEFLATE bytes");
        } catch (DataFormatException ex) {
            throw new IllegalStateException("Damaged EPK3 DEFLATE stream", ex);
        }
    }

    private static IllegalStateException damaged(String message) {
        return new IllegalStateException("Damaged emoji.pack: " + message);
    }
}
